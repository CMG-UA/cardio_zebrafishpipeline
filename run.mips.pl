#!/usr/bin/perl

$|++;

use strict;
use warnings;

use Cwd qw(abs_path getcwd);
use Data::Dumper;
use File::Spec::Functions;
use File::Basename;
use File::Path qw(rmtree make_path);
use FindBin; 
use Getopt::Std;
use Text::Table;
use XML::Simple;
use IO::Handle;

# add current dir to include the packages
use lib $FindBin::Bin;
use msamplesheet;
use mail;
use pbs;


##########@@@@@@@@@@@@@@@  COMMAND LINE ARGUMENTS  & LOAD CONFIG FILES  @@@@@@@@@@@@@@@##########

my %opts;
getopts('m:l:tpg', \%opts);  # option are in %opts
if (! defined($opts{'m'}) || $opts{'m'} eq '') {
    die("Main configuration file file is not specified (option -,)");
}
my $configfile = $opts{'m'};
$configfile = abs_path($configfile);
if (!-e $configfile) {
    die("Main configuration file '$configfile' does not exist");
}

if (! defined($opts{'l'}) || $opts{'l'} eq '') {
    die("Local configuration file file is not specified (option -,)");
}
my $localconfigfile = $opts{'l'};
$localconfigfile = abs_path($localconfigfile);
if (!-e $localconfigfile) {
    die("Local configuration file '$configfile' does not exist");
}

## config files
my $config = readXml($configfile);
my $localconfig = readXml($localconfigfile);


# Skip certain steps for debugging purposes to speed up
# All files are still made (overwritten) but no joba are executed 
# Possible modes: t(rim), p(ipeline of UMCN), g(enotyping), 
# All upstream step are also skipped (e.g. trimming is skipped when -p is given)

# default no steps are skipped
$config->{skip} = 0;
if (defined $opts{'t'}) {
=cut

	my $cmdf = "cat @r1_files > $freads";
	system($cmdf) == 0 or dieWithGrace("Making megapool R1 failed", $project, $config, $localconfig);
	my $cmdr = "cat @r2_files > $rreads";
	system($cmdr) == 0 or dieWithGrace("Making megapool R2 failed", $project, $config, $localconfig);
	
	if (@i1_files && @i2_files) {
		my $cmdi1 = "cat @i1_files > $i1reads";
		system($cmdi1) == 0 or dieWithGrace("Making megapool I1 failed", $project, $config, $localconfig);
		my $cmdi2 = "cat @i1_files > $i2reads";
		system($cmdi2) == 0 or dieWithGrace("Making megapool I2 failed", $project, $config, $localconfig);
	}
	else {
		# fill this in the config.txt file
		$ireads = 'null';
	}

=cut
    print "Skip trimming with cutadapt\n";
    $config->{skip} = 1;
}
if (defined $opts{'p'}) {
    print "Skip UMCN pipeline (extraction target sequence + mapping)\n";
    $config->{skip} = 2;
}
if (defined $opts{'g'}) {
    print "Skip SNP panel genotyping\n";
    $config->{skip} = 3;
}

## GENERAL SETTINGS
my $readlength = $config->{readlength};
#my $snppanel = $config->{snpfile};
my $waittime = $config->{wait};

## DEV/PROD SPECIFIC SETTINGS
my $adminmail = $localconfig->{email}->{admin};
my $notifymail = $localconfig->{email}->{notify};
my $frommail = $localconfig->{email}->{from};
my $codedir = $localconfig->{code};


while (my $rundir = scanForNew($config, $localconfig)) {
	my $project = basename($rundir);
	my $errormail = mail->new($frommail,$adminmail,"Problems encountered with MIPS run $project");

	##########@@@@@@@@@@@@@@@  RUN FOLDER PREP  @@@@@@@@@@@@@@@##########
	# open log
	open LOG, ">".catfile($rundir, "RunTimeOutput.txt");
	print LOG "Analysing project: $project \n";
	print LOG "1. Preparations\n";

	my $statusfile = catfile($rundir, $config->{status});
	
	print LOG "\tMake subdirectories\n";

	# Make subdirs
	my $scriptsdir = catfile($rundir, $config->{dirs}->{scripts});
	if (! -d $scriptsdir) {
		mkdir $scriptsdir or dieWithGrace("Can't create the qsub script directory '$scriptsdir'", $project, $config, $localconfig);
	}
	my $jodir =  catfile($rundir, $config->{dirs}->{jo});
	if (! -d $jodir) {
		mkdir $jodir or dieWithGrace("Can't create the qsub output directory '$jodir'", $project, $config, $localconfig);
	}
	my $tmpdir =  catfile($rundir, $config->{dirs}->{tmp});
	if (! -d $tmpdir) {
		mkdir $tmpdir or dieWithGrace("Can't create the qsub output directory '$tmpdir'", $project, $config, $localconfig);
	}

	## delete old MIPS dirs (unless we're re-using it)
	if ($config->{skip} < 2 && glob("${rundir}/MIP_out_*")) {
			map { -d $_ ? rmtree($_) : () } glob("${rundir}/MIP_out_*");
	}

	my $failedjobs = catfile($rundir, $config->{pbs}->{failed});
	# print "DEBUG failed file '$failedjobs'\n";
	## delete old failed jobs file
	if ( -e $failedjobs) {
		unlink($failedjobs);
	}


	##########@@@@@@@@@@@@@@@  READ SAMPLESHEET  @@@@@@@@@@@@@@@##########
	my $samplesheetfile = catfile($rundir, $config->{samplesheet}->{file});
	my $megapoolfile = catfile($rundir, $config->{megapool});
	print LOG "\tRead in samplesheet $samplesheetfile and generate megapool input file\n";

	if (! -e $samplesheetfile) {
		$errormail->addtext("Samplesheet was not found");
		$errormail->sendmail();
		setStatus($rundir, $config, -1);
		close LOG;
		next;
	}
	my $samplesheet = msamplesheet->new($samplesheetfile);

	open my $in, '>', $megapoolfile;
	print $in $samplesheet->generateMIPSSamplesheet();
	close $in;


	##########@@@@@@@@@@@@@@@  POOL FASTQ FILES INTO R1/R2 FASTQ FILES  @@@@@@@@@@@@@@@##########
	print LOG "\tMerge all FASTQ files to megapool";
	
	# 3 modes for indexes (MiSeq data)
	## a. Single index, file = specify index file
	## b. Single index in header = set index file 'null' in config
	## c. Double index in header = set index file 'null' in config
	## For convenience this pipeline only prepare MIPS with index in FASTQ file (options b/c)

	# megapool file names
	my $r1file = catfile($rundir, $config->{data}->{forward});
	my $r1gzipfile = $r1file . ".gz";
	my $r2file = catfile($rundir, $config->{data}->{reverse});
	my $r2gzipfile = $r2file . ".gz";

	my @samples = $samplesheet->getSamples();

	if ($config->{skip} > 1) {
		print LOG " -->SKIPPED\n";
	}
	else {
		open my $r1, '>', $r1file;
		open my $r2, '>', $r2file;

		foreach my $sample (@samples) {
			if (! $samplesheet->getSampleFASTQs($sample, 'fwd')) {
				next;
			}
			# get index data
			my (@i1reads, @i2reads);
			my $i1file = shift @{$samplesheet->getSampleFASTQs($sample, 'i1')};
			# die("debug");
			@i1reads = split(/\n/, `zcat $i1file`);

			if ($samplesheet->isDoubleIndex()) {
				my $i2file = shift @{$samplesheet->getSampleFASTQs($sample, 'i2')};
				@i2reads = split(/\n/, `zcat $i2file`);
			}
			
			## add index to header and print data to megapool file
			my $r1file = shift @{$samplesheet->getSampleFASTQs($sample, 'fwd')};
			my @r1reads = split(/\n/, `zcat $r1file`); 
			my $r2file = shift @{$samplesheet->getSampleFASTQs($sample, 'rev')};
			my @r2reads = split(/\n/, `zcat $r2file`); 
			
			for (my $i=0; $i < scalar(@r1reads); $i+=4) {
				# line $i: header
				# line $i+1: read (or index)
				# line $i+2: extra header line
				# line $i+3: qual data

				my $index;
				if ($samplesheet->isDoubleIndex()) {
					$index = join("", ($i1reads[$i+1], $i2reads[$i+1]))
				}
				else {
					$index = $i1reads[$i+1];
				}

				## replace last header column with index
				my @r1header = split(/:/, $r1reads[$i]); 
				print $r1 join(":", (@r1header[0..$#r1header -1], $index)) . "\n";
				my @r2header = split(/:/, $r2reads[$i]); 
				print $r2 join(":", (@r2header[0..$#r2header -1], $index)) . "\n";

				# print rest of data
				print $r1 join("\n", @r1reads[$i+1..$i+3]) . "\n";
				print $r2 join("\n", @r2reads[$i+1..$i+3]) . "\n";
			}
		}

		close $r1;
		close $r2;

		# gzip files
		system("gzip $r1file; gzip $r2file");

		print LOG " --> DONE\n";

	}


   ##########@@@@@@@@@@@@@@@  RUN SNAKEMAKE MIPS PIPELINE DEMULTIPLEXING  @@@@@@@@@@@@@@@##########
   #Run setup
   my $mipspipeline = $config->{binaries}{mipspipeline};
   my $snakemake = $config->{binaries}{snakemake};                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        
   my $mips_design = $config->{settings}{mips_design};    #mips design file
   my $targetfile = catfile($rundir, $config->{targets});
   system('awk \'BEGIN {OFS="\t" ;chr="chr"} { print chr$4,$13,$14,$1,$19 }\' '.$mips_design.' | tail -n +2 > ' . $targetfile) == 0 or dieWithGrace("Making targets.bed file failed", $project, $config, $localconfig);
   chdir($rundir);
   system("cp $mips_design .");
   system("cp -r $mipspipeline .");
   system("sed -i -e 's|\"forward_randomtag\" : 5|\"forward_randomtag\" : 8|g' -e 's|\"reverse_randomtag\" : 5|\"reverse_randomtag\" : 0|g' config.json");
   system("sed -i -e 's|\"nextseq\" : false|\"nextseq\" : true|g' config.json");
   system("sed -i -e 's|\"Undetermined_S0_L001_R1_001.fastq.gz\"|\"megapool_R1.fastq.gz\"|g' Snakefile");
   system("sed -i -e 's|\"Undetermined_S0_L001_R2_001.fastq.gz\"|\"megapool_R2.fastq.gz\"|g' Snakefile");
   
   # run snakemake pipeline
   system("$snakemake -R --until Preprocess --cluster \"qsub -q zebrafish -l mem=5G\" --latency-wait 100 --jobs 1");
   # move .fastq results to jobtmp folder for BATCH-GE processing.
   system("find fastq_per_sample/*fastq ! -name unknown* -exec mv {} $tmpdir \\\;");
   


   ##########@@@@@@@@@@@@@@@  BATCH-GE GENOTYPING  @@@@@@@@@@@@@@@##########

	# compress fastq files
	my $job = pbs->new($rundir, "Map.$project.MIPS", $jodir, $tmpdir, $scriptsdir, $config->{pbs}->{failed});
	$job->header(16, 10, $rundir, $config->{pbs}->{queue},  $config->{pbs}->{account}, '', $localconfig->{email}->{admin});
	$job->command("cd $tmpdir; for i in *fastq; do gzip -q \$i; done", 1, 0);
	$job->finish();

	if ($config->{skip} > 2) {
		print LOG " --> SKIPPED\n";
	}

	else {
		$job->run();
		
		while (! $job->finished()) {
			sleep $waittime;
		}
		checkPbsErrors($rundir, $config, $localconfig);

		print LOG " --> DONE\n";
	}



	print LOG "\tPrepare experiment file\n";

    my $python = $config->{binaries}{python};
   	my $batchge = $config->{binaries}{batchge};

	my $genome = $config->{reference}{danio};
	my $cutsites = $config->{settings}{batchge_cutsites};
	my $repairs_strings = $config->{settings}{batchge_repair};
	my @repairs = split /,/, $repairs_strings;

	#Get line number of start of samples
	my $samplesheet_path = catfile($rundir, "SampleSheet.csv");
	open my $samplesheet_handle, '<', $samplesheet_path;
	my $sample_linenum;
	while (<$samplesheet_handle>) {
		$sample_linenum = $samplesheet_handle->input_line_number(), last if /Sample_ID,Sample_Name,Sample_Plate,Sample_Well,I7_Index_ID,index,I5_Index_ID,index2,Sample_Project,Description/
	}
	close $samplesheet_handle;

	#Loop of all repair sequences in the config files. Each repair sequence should correspond to 1 roi. roi should be numbered accordingly in the roi file.
	foreach (my $j = 0; $j < scalar(@repairs); $j++) {
		#From here roidir is the roi specific output directory for BATCH-GE
		my $roidir = catfile($rundir, "roi$j");
		if (! -d $roidir) {
			mkdir $roidir or dieWithGrace("Can't create the roi specific output directory '$roidir'", $project, $config, $localconfig);
		}
		#Create main result dir
		my $rdir = $config->{dirs}->{results};
		my $resultdir =  catfile($roidir, $rdir);
		if (! -d $resultdir) {
			mkdir $resultdir or dieWithGrace("Can't create the qsub output directory '$resultdir'", $project, $config, $localconfig);
		}
		my $repair = $repairs[$j];


		my $samplestring = `awk -vORS=, 'BEGIN{FS=\",\"} NR>$sample_linenum {print \$1 }' $samplesheet_path`;
		## experiment config
		my $expconfigt = $config->{settings}{batchge_main};
		my $expconfigt_full = catfile($codedir, 'config', $expconfigt);
		if (! -e $expconfigt_full) {
			dieWithGrace("The BATCHGE experiment template wasn't found in the script dir '$codedir'. Check if the main config file is correct", $project, $config, $localconfig);
		}

		#Split sample string into separate groups of n samples for parallel analysis
		my $group_size = 50;
		my @split_samplestring = split(',', $samplestring);
		my @sample_groups;
		push @sample_groups, [splice @split_samplestring, 0, $group_size] while @split_samplestring;
		my $n_groups = scalar @sample_groups;

		#loop over sample groups and prepare and start batch-ge jobs
		my @jobs;
		foreach (my $i = 0; $i < $n_groups; $i++) {
			my $sample_group = join(",", @{@sample_groups[$i]});

			my $group_scriptsdir = catfile($roidir, "job$i", $config->{dirs}->{scripts});
			my $group_jodir = catfile($roidir, "job$i", $config->{dirs}->{jo});
			my $group_tmpdir =  catfile($roidir, "job$i", $config->{dirs}->{tmp});
			my $group_resultdir = catfile($roidir, "job$i", $config->{dirs}->{results});

			chdir($roidir);
			system("mkdir job$i");

			# Make subdirs
			if (! -d $group_scriptsdir) {
				mkdir $group_scriptsdir or dieWithGrace("Can't create the qsub script directory '$group_scriptsdir'", $project, $config, $localconfig);
			}
			if (! -d $group_jodir) {
				mkdir $group_jodir or dieWithGrace("Can't create the qsub output directory '$group_jodir'", $project, $config, $localconfig);
			}
			if (! -d $group_tmpdir) {
				mkdir $group_tmpdir or dieWithGrace("Can't create the qsub output directory '$group_tmpdir'", $project, $config, $localconfig);
			}
			if (! -d $group_resultdir) {
				mkdir $group_resultdir or dieWithGrace("Can't create the qsub output directory '$group_resultdir'", $project, $config, $localconfig);
			}

			my $expconfig = catfile($roidir, "job$i", $expconfigt);
			system("sed -e 's|%INPUT%|$tmpdir/|g' $expconfigt_full | sed -e 's|%SAMPLES%|$sample_group|g' | sed -e 's|%REFERENCE%|$genome|g' | sed -e 's|%ROI%|roi$j|g' | sed -e 's|%RESULT%|$group_resultdir/|g' | sed -e 's|%CUTSITES%|$cutsites|g' | sed -e 's|%REPAIR%|$repair|g' > $expconfig") == 0 or dieWithGrace("Making experiment config file failed", $project, $config, $localconfig);

			print LOG "\tCalculate knock in efficiency";

			## create script using pbs.pm instead of from template file
			$job = pbs->new("$roidir/job$i", "Genotype.$project.BATCHGE", $group_jodir, $group_tmpdir, $group_scriptsdir, $config->{pbs}->{failed});
			#  my ($self, $cpu, $mem, $queue, $account, $additional, $email) = @_;
			$job->header(16, 10, "$roidir/job$i", $config->{pbs}->{queue},  $config->{pbs}->{account}, '', $localconfig->{email}->{admin});
			$job->command("perl $batchge --ExperimentFile $expconfig", 1, 1);

			#Run zebrafish variant parser script to fill in insertion sequences in BATCH-GE's Variant result file
			$job->command("$python ${codedir}/zebravisVariantParser.py -r $roidir/job$i -i roi$j", 1, 1);
			$job->finish();
			push(@jobs, $job);
		}

		#Wait for jobs to finish
		if ($config->{skip} > 2) {
			print LOG " --> SKIPPED\n";
		}
		else {
			for my $job (@jobs){
				$job->run();
			}
			sleep $waittime;
			for my $job (@jobs){
				#If job already finished before "finish" check, finished() function throws an error. Catch that error and continue.
				my $job_finished = 0;
				eval{
						$job_finished = $job->finished();
					};
					if ($@) {
						print LOG $@;
						print LOG "job likely already exited\n";
						$job_finished = 1;
				};
				while (! $job_finished) {
					eval{
						$job_finished = $job->finished();
					};
					if ($@) {
						print LOG $@;
						print LOG "job likely already exited\n";
						$job_finished = 1;
					};
					sleep $waittime;
				}
			}

			checkPbsErrors($roidir, $config, $localconfig);
			print LOG " --> DONE\n";
		}

		#Stich results back together
		foreach (my $i = 0; $i < $n_groups; $i++) {
			#get result and log files
			chdir("$roidir/job$i");
			my @result_files=`find . -name "*.txt" -o -name "*.log*"`;
			#Copy result dir structure to main resultdir
			system("rsync -a --include '*/' --exclude '*' ./job_results/ $resultdir");
			#Copy and merge this run's result and log file contents to main result dir.
			foreach my $file (@result_files){
				$file =~ s/\R//g;
				system("cat $file >> $roidir/$file");
			}
		}

		##########@@@@@@@@@@@@@@@  FINALIZE  @@@@@@@@@@@@@@@##########
		# 	- Parse result file Efficiencies.txt
		# 	- Send mail to notify analysis is finished and results to researcher

		print LOG "4. Finalize\n";
		print LOG "\tPrepare results\n";

		my $filename = $config->{results};
		my $resultfile = `find $resultdir -type f -name '$filename' | head -1`;
		print "DEBUG: $resultfile\n";

		chomp($resultfile);
		if (! -e $resultfile) {
			dieWithGrace("Resultsfile $resultfile was not found\n");
		}

		my %results;
		my $csample;
		#my $table = Text::Table->new("Sample", "Mutagenesis rate", "Repair efficiency");

		open $in, '<', $resultfile;
		while (<$in>) {
			if ($_ =~ /^\tSample number (\S*) from/) {
				$csample = $1;
			}
			if ($_ =~ /Mutagenesis efficiency for \S* is (\S*) \((\d*) readpairs with indel\(s\) versus (\d*) readpairs without indel\(s\)\)/) {
				$results{$csample}{'mut'} = $2;
				$results{$csample}{'total'} = $2 + $3;
			}
			if ($_ =~ /Repair efficiency for \S* is (\S*) \((\d*) readpairs with repair versus (\d*) readpairs in total\)/) {
				$results{$csample}{'repair'} = $2;
			}
		}

		## put data in array
		my @output;
		push (@output, "Staalnaam\tTotaal\tIndel\tKI");
		for my $sample (keys %results) {
			my ($mut, $repair);
			my $total =  $results{$sample}{'total'};
			if (exists $results{$sample}{'mut'}) {
				$mut = $results{$sample}{'mut'};
			}
			else {
				$mut = 0;
			}

			if (exists $results{$sample}{'repair'}) {
				$repair = $results{$sample}{'repair'};
			}
			else {
				$repair = 0;
			}

			push(@output, "$sample\t$total\t$mut\t$repair");
		}

		#$table->load(@output);


		print LOG "\n\tSend mail\n";
		my $finishmail = mail->new($frommail, $notifymail, "BATCH-GE analysis for $project is finished");

		$finishmail->addtext("The following table shows the BATCH-GE efficiencies output:\n\n");
		$finishmail->addtext(join("\r\n", @output));
		# print to file
		open my $outputfile, '>', catfile($roidir, "output_mail.txt");
		print $outputfile join("\r\n", @output);
		close $outputfile;

		#my $texttable = $table->table();
		#$finishmail->addtext($texttable);

		$finishmail->sendmail();
		chdir("$rundir/..");
		print LOG " --> DONE\n";
	}
	## set status
	setStatus($rundir, $config, 1);

}

close LOG;

print "DONE\n";

##########@@@@@@@@@@@@@@@  SUBROUTINES  @@@@@@@@@@@@@@@##########

sub checkPbsErrors {
    my ($rundir, $config, $localconfig) = @_;
	my $project = basename($rundir);
    my $failedjobs = catfile($rundir, $config->{pbs}->{failed});
    if (-e $failedjobs) {
        my $message = `cat $failedjobs`;
        $message = "The following jobs(s) reported to be failed:\n" . $message;
        dieWithGrace($message, $project, $config, $localconfig);
    }
}

sub dieWithGrace {
	my ($message, $project, $conf, $localconf) = @_;
	my $rundir = catfile($localconf->{incoming}, $project);
	my $statusfile = catfile($rundir, $conf->{status});
	my $adminmail = $localconf->{email}->{admin};
	my $notifymail = $localconf->{email}->{notify};
	my $frommail = $localconf->{email}->{from};

	my $errormail = mail->new($frommail, $adminmail, "Problem encountered with MIPS run $project");
	$errormail->addtext("$message\n");

	## send mail
	$errormail->sendmail();

	print  "####################\n";   
	print  "# CRITICAL PROBLEM #\n";
	print  "####################\n";
	print  "\n";
		print  "Problems encountered with MIPS run $project\n";
		print  "Message was:\n";
		print  "$message\n\n";
		print  "\tMail sent to notify admin of crash.\n";
		print  "Monitor will exit now\n";
		## it died, so there was an error => set project to error state.
		setStatus($rundir, $config, -1);
		exit;

}


sub doChecksums {
    my ($projectdir, $destination, $suffix, $copyres, $files) = @_;

    ## make checksum remotely (globstar to enable recursive globbing)
    my $md5file = catfile($projectdir, "md5sum." . $suffix . ".txt");
    my $md5resfile = catfile($projectdir, "md5sum." . $suffix . ".check.txt");
    my $md5error = catfile($projectdir, "md5sum." . $suffix . ".error.txt");
    if (-e $md5file) {unlink $md5file};
    if (-e $md5resfile) {unlink $md5resfile};
    if (-e $md5error) {unlink $md5error};

    ## Checksum specific files at destination
    if (scalar($files)) {
        my $command = "cd $destination; md5sum @{$files} >> $md5file";
        print "\n$command\n";
        system_bash($command) == 0 or return(1);
        $command = "cd $projectdir && md5sum -c $md5file > $md5resfile 2> $md5error";
        print "\n$command\n";
        system($command)== 0 or return(1);
    }

    ## Checksum everyting at destination
    else {
        my $command = 'set -e; shopt -s globstar; cd ' . $destination . ' && for i in **; do [[ -f "$i" ]] && ! [[ $i =~ md5sum.* ]] && md5sum "$i" >> ' . $md5file . '; done';
        print "\n$command\n";
        system_bash($command) == 0 or return(1);
        $command = "cd $projectdir && md5sum -c $md5file > $md5resfile 2> $md5error";
        print "\n$command\n";
        system($command) == 0 or return(1);
    }

    ## remove empty error file
    unlink $md5error;


    ## Copy results to destination
    if ($copyres) {
        system("rsync -av $md5file $md5resfile $destination > /dev/null 2>&1")== 0 or return(1);
    }

    return(0);

}

sub getStatus {
    my ($dir, $config) = @_;
    my $statusfile = catfile($dir, $config->{status});
    if (-e $statusfile) {
        my $status = `cat $statusfile`;
        chomp($status);
        return($status);
    }
    else {
        return(0);
    }
}

sub setStatus {
    my ($dir, $config, $status) = @_;
    my $statusfile = catfile($dir, $config->{status});
	# print "DEBUG statusfile: '$statusfile'\n";
    open my $out, '>', $statusfile;
    print $out "$status\n";
    close $out;
    return(0);
}

sub readXml {
	my $data = shift;
	my $xml = XML::Simple->new();	
	return($xml->XMLin($data));
}

sub scanForNew {
	my ($conf, $localconf) = @_;
	my $incomingdir = $localconf->{incoming};
	foreach my $project (split(/\n/, `cd $incomingdir && find -maxdepth 1 -type d | cut -d/ -f2`))  {
		# must have Illumina project syntax, other dirs are ignored
		if ($project !~ /^\d{6}_[A-Za-z0-9]*_\d*_[A-Za-z0-9]*/) {
			##print "skip dir: $project\n";
			next;
		}
		my $projdir = catfile($incomingdir, $project);
		my $trigger = catfile($projdir, $conf->{trigger});
		my $statusfile = catfile($projdir, $conf->{status});
		if (! -e $trigger) {
			next;
		}
		# status values (1=finished;0=ongoing;-1=error)
		if (! -e $statusfile) {
			setStatus($projdir, $config, 0);
			print "Found new project: $project\n";
			return($projdir);
		}
		else {
			my $status = getStatus($projdir, $conf);
			chomp($status);
			if ($status == 0) {
				print "Found new project: $project\n";
				return($projdir);
			}
		}
		
	}
	print "No new project\n";
	return 0;
}

sub system_bash {
    my @args = ( "bash", "-c", shift );
    system(@args);
}
