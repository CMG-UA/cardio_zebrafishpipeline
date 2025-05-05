package pbs;

use strict;
use warnings;
use Data::Dumper;
use File::Basename;
use File::Path qw(make_path);
use File::Spec::Functions;
use XML::Simple;

##########@@@@@@@@@@@@@@@  CONSTRUCTOR  @@@@@@@@@@@@@@@##########

sub new {
	my ($class, $projectdir, $jobname, $jodir, $tmpdir, $scriptdir, $failedfile) = @_;
	
	my $self = {
		dir => $projectdir,
		name => $jobname,
		scripts => $scriptdir,
		jo => $jodir,
		tmpfiles => $tmpdir,
		failed => $failedfile
	};

	## Make subdirectories 
	foreach my $subdir ($self->{scripts}, $self->{jo}, $self->{tmpfiles}) {
		if (! -d $subdir) { make_path $subdir }		
	}

	$self->{failed} = catfile($self->{dir}, $self->{failed});

	bless $self, $class;
	return $self;	
}



##########@@@@@@@@@@@@@@@  METHODS  @@@@@@@@@@@@@@@##########

sub command {
        my ($self, $run_command, $echo , $check) = @_;

	my $failedfile = $self->{failed};

        ## append mode
        open my $out, '>>', $self->{script} or return 0;

        print $out "echo 'Command:'\n";
        print $out "echo '========'\n";
        if ($echo) {
                my $echo_command = $run_command;
                $echo_command =~ s/"/\\"/g;
                print $out "echo \"$echo_command\"\n";
        }
        print $out "$run_command\n";

	## check status file of previous command
	if ($check) {
	        print $out 'if [ "$?" -ne "0" ] ; then'."\n";
        	print $out "  echo '$run_command' >> " . $self->{failed} ." \n";
	        print $out '  exit $?'."\n";
        	print $out "fi\n\n";
	}

	close $out;
}


sub finish {
        my $self = shift;

        ## append mode
        open my $out, '>>', $self->{script} or return 0;

        print $out "\n\necho 'End Time : ' `date`\n";
        print $out "printf 'Execution Time = \%dh:\%dm:\%ds\\n' \$((\$SECONDS/3600)) \$((\$SECONDS%3600/60)) \$((\$SECONDS%60))\n";

	close $out;
}


sub finished {
	my $self = shift;

	my $command = "qstat -x " . $self->{jobid};
	$self->{qstatxmlstring} = `$command`;
	chomp($self->{qstatxmlstring});
	
	$self->{qstatxml} = $self->_readXml();
	if ($self->{qstatxml}->{Job}->{job_state} eq 'C') {
		$self->{finished} = 1;
	}
	else {
		$self->{finished} = 0;
	}
	return $self->{finished};
}

sub header {
        my ($self, $cpu, $mem, $cwd, $queue, $account, $additional, $email) = @_;
	
        my $dir = $self->{dir};
        my $jodir = $self->{jo};
	my $jobname = $self->{name};
	$self->{script} = catfile($self->{scripts}, $jobname . ".sh");
	
	open my $out, '>', $self->{script} or return 0;

        print $out "#!/usr/bin/env bash\n";
        print $out "#PBS -m a\n";
        print $out "#PBS -M $email\n";
        print $out "#PBS -d $cwd\n";
        print $out "#PBS -l nodes=1:ppn=$cpu,mem=$mem"."g\n";
        print $out "#PBS -N $jobname\n";
        print $out "#PBS -o $jodir/$jobname.o.txt.\$PBS_JOBID\n";
        print $out "#PBS -e $jodir/$jobname.e.txt.\$PBS_JOBID\n";
        print $out "#PBS -V\n";
        print $out "#PBS -q $queue\n";
        if ($account) {
                print $out "#PBS -A $account\n";
        }
        ## additional PBS directives?
        if (ref($additional) eq 'ARRAY') {
                foreach my $directive (@$additional) {
                        print $out "$directive\n";
                }
        }
        print $out "\necho 'Running on : ' `hostname`\n";
        print $out "echo 'Start Time : ' `date`\n";

	close $out;
}


sub run {
	my $self = shift;
	
	my $job = $self->{script};
	$self->{jobid} = `qsub $job`;
	chomp($self->{jobid});

	## Check if job is submitted OK
	while ($self->{jobid} !~ m/^\d+\..*/) {
		sleep 5;
		my $tmpfile = `mktemp`;
		chomp($tmpfile);
		my $return = `qstat -x 2>$tmpfile | grep $self->{script}`;
		chomp($return);
		my $errorreturn = `cat $tmpfile`;
		if (! ($return or $errorreturn)) {
			$self->{jobid} = `qsub $self->{script}`;
			chomp($self->{jobid});
		}
		system("rm $tmpfile");
	}

	return $self->{jobid};

}

##########@@@@@@@@@@@@@@@  INTERNAL METHODS  @@@@@@@@@@@@@@@##########

sub _readXml {
	my $self = shift;
	my $xml = XML::Simple->new();	
	return($xml->XMLin($self->{qstatxmlstring}));
}


1;

