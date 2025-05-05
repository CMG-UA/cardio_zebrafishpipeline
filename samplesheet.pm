package samplesheet;

use strict;
use warnings;

use Data::Dumper;
use File::Basename;
use File::Path qw(make_path);
use File::Spec::Functions;
use Switch;


##########@@@@@@@@@@@@@@@  CONSTRUCTOR  @@@@@@@@@@@@@@@##########

sub new {
	my ($class, $samplesheetfile) = @_;

	my $self = {
		file => $samplesheetfile,
		dir => dirname($samplesheetfile),
		project => basename(dirname($samplesheetfile)) 		
	};

	bless $self, $class;
	$self->_parse();

	return $self;
}

##########@@@@@@@@@@@@@@@  INTERNAL METHODS  @@@@@@@@@@@@@@@##########

sub _parse {
	my ($self) = @_;

	my $flag;
	open IN, $self->{file};
	while (<IN>) {
		next if ($_ =~ /^$/);
		next if ($_ =~ /^#/);
		chomp;
		if ($_ =~ /^\[Header\]/) {
			$flag = 'header';
			next;
		}
		elsif ($_ =~ /^\[Reads\]/) {
			$flag = 'reads';
			next;
		}
		elsif ($_ =~ /^\[Settings\]/) {
			$flag = 'settings';
			next;
		}
		elsif ($_ =~ /^\[Data\]/) {
			$flag = 'data';
			next;
		}

		if (! $flag ) {
			return(1)
			#something went wrong, quit
		}

		my @columns = split(",", $_);

		switch($flag) {
			case /header|settings/ {
				$self->{$flag}->{$columns[0]} = $columns[1];
			}
		
			case 'reads' {
				if (! $self->{reads}) {
					push(@{$self->{reads}}, $columns[0]);
				}
			}

			case 'data' {
				## parse sample header
				if ($_ =~ /^Sample_ID,Sample_Name,/ ) {
					my $index = 0;
					foreach my $hcolumn (split(",", $_)) {
						$self->{hvalues}->{$index} = lc($hcolumn);
						$self->{hcolumns}->{lc($hcolumn)} = $index;
						$index++;
					}
				}

				else {
					my $index = 0;
					foreach my $scolumn (split(",", $_)) {
						if (! $index) {
							$self->{currentsample} = $scolumn;
						}
						else {
							## key value pair of header, value per column
							$self->{samples}->{$self->{currentsample}}->{$self->{hvalues}->{$index}} = $scolumn;
						}
						$index++;
					}
				}
			}
		}
	}
}



##########@@@@@@@@@@@@@@@  METHODS  @@@@@@@@@@@@@@@##########

sub determineSeqtype {
        my $self = shift;
        if ($self->{project} =~ /^\d{6}_M/i) {
		$self->{seqtype} = 'MiSeq';
                return('MiSeq');
        }
        elsif ($self->{project} =~ /^\d{6}_N/i) {
		$self->{seqtype} = 'NextSeq';
                return('NextSeq');
        }
}

sub getHeader {
	my $self = shift;
	return $self->{hcolumns};
}

sub getSampleDescription {
	my ($self, $sample) = @_;
	return($self->{samples}->{$sample}->{description});
}

sub getSampleDestination {
	my ($self, $sample) = @_;
	my @sample_project = split(/@/, $self->getSampleUD($sample));
	if ($sample_project[1]) { 
		return $sample_project[1];
	}
	else {
		return 0;
	}
}

sub getSamples {
	my ($self) = @_;	
	return keys %{$self->{samples}};
}

sub getSampleIndex {
	my ($self, $sample) = @_;
	return($self->{samples}->{$sample}->{'index'});
}

sub getSampleIndex2 {
	my ($self, $sample) = @_;
	return($self->{samples}->{$sample}->{'index2'});
}

sub getSampleInfo {
	my ($self, $sample) = @_;
	return($self->{samples}->{$sample});
}

sub getSampleUD {
	my ($self, $sample) = @_;
	return($self->{samples}->{$sample}->{'sample_project'});
}

sub getSampleUser {
	my ($self, $sample) = @_;
	my @sample_project = split(/@/, $self->getSampleUD($sample));
	if ($sample_project[0]) { 
		return $sample_project[0];
	}
	else {
		return 0;
	}
}

sub print {
	my ($self, $arg) = @_;
	if (! $arg) {
		print Dumper($self);
	}
	else {
		print Dumper($arg);
	}
}


1;
