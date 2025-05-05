package msamplesheet;

use strict;
use warnings;
use Data::Dumper;
use File::Basename;
use File::Path qw(make_path);
use File::Spec::Functions;

use parent 'samplesheet';



##########@@@@@@@@@@@@@@@  CONSTRUCTOR  @@@@@@@@@@@@@@@##########

sub new {
	my ($class, $samplesheetfile, $dir) = @_;

	my $self = {
		file => $samplesheetfile,
		dir => dirname($samplesheetfile),
		project => basename(dirname($samplesheetfile)),
		usersfile => catfile(dirname($samplesheetfile), ".knownusers")		
	};

	bless $self, $class;

	## use the general _parse function from the Class samplesheet
	$self->_parse();

	$self->_loadSmipsSamples();

	return $self;
}


sub getSampleFASTQs {
	my ($self, $sample, $direction) = @_;
	# print Dumper($self->{samples}->{$sample}->{$direction});
	# print $direction . "\n";
	# exit;
	return $self->{samples}->{$sample}->{$direction};

}

##########@@@@@@@@@@@@@@@  INTERNAL METHODS  @@@@@@@@@@@@@@@##########

sub _deleteSample {
	my ($self, $sample) = @_;
	delete $self->{samples}->{$sample};
}

sub _findFASTQs {
	my ($self, $sample) = @_;
	my $dir = $self->{dir};
	push(@{$self->{samples}->{$sample}->{fwd}}, glob("${dir}/${sample}_S*_R1_*.fastq.gz"));
	push(@{$self->{samples}->{$sample}->{rev}}, glob("${dir}/${sample}_S*_R2_*.fastq.gz"));
	push(@{$self->{samples}->{$sample}->{i1}}, glob("${dir}/${sample}_S*_I1_*.fastq.gz"));
	push(@{$self->{samples}->{$sample}->{i2}}, glob("${dir}/${sample}_S*_I2_*.fastq.gz"));
}

sub _loadSmipsSamples {
	## delete non-smMIPs samples
	## link FASTQ files to samples 
	my $self = shift;
	
	
	## Go over samples
	foreach my $sample ($self->getSamples()) {
		# if ( $self->getSampleUD($sample) !~ /smMIPS/i) {
			# print "DEBUG: ignore sample $sample: ". $self->getSampleUD($sample) ."\n";
			# $self->_deleteSample($sample);
			# next;
		# }
		if ($self->determineSeqtype() eq 'MiSeq') {
			$sample = $self->_renameSample($sample);
		}

		$self->_findFASTQs($sample);
	}

}

sub _renameSample {
	my ($self, $sample) = @_;
                        
	my $sampleinfo = delete $self->{samples}->{$sample};
	$sample =~ s/^ +| +$|^\.+|\.+$//g;
	$sample =~ s/_+| +|\.+|\(+|\)+|\/|'+/-/g;
	
	$self->{samples}->{$sample} = $sampleinfo;	

	# to use the new one in the loop
	return($sample);

}




##########@@@@@@@@@@@@@@@  METHODS  @@@@@@@@@@@@@@@##########

sub generateMIPSSamplesheet {
	## create megapool sample file content as 1 string variable
	## returns that variable and saved it to file in main script

	## Structure sample line:
	## <index>[+<index2>]\t<sampleID>\n

	my $self = shift;

	my $lines;
	foreach my $sample ($self->getSamples()) {
		my $line = $self->getSampleIndex($sample);

		## if double indexed, syntax of first column is <index>+<index2>
		if($self->isDoubleIndex()) {
			$line .= "+" . $self->getSampleIndex2($sample);
		}
		$line .= "\t" . $sample . "\n";
		## append line
		$lines .= $line;
	}
	return $lines;
}

sub isDoubleIndex {
	## Return TRUE if prep has 2 indexes
	my $self = shift;
	my $header = $self->getHeader();
	# print Dumper($header);
	if (exists $header->{'index2'}) {
		return 1;
	}
	else {
		return 0;
	}
}

1;

