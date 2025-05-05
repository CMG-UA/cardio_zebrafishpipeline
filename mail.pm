package mail;

use strict;
use warnings;
use Carp 'croak';

sub new{
    my ($class,$from,$to,$subject) = @_;

    my $self = {
        'from'      => $from,
        'to'        => $to,
        'subject'   => $subject,
        'body'      => "",
        'cmd'       => "sendmail -t"
    };

    bless $self,$class;
    return $self;
}

sub addtext {
    my ($self, $txt) = @_;
    $self->{'body'} .= $txt;
}

sub sendmail {
    my ($self) = @_;

    open(my $process,"| ".$self->{'cmd'}) || croak("Couldn't start sendmail process");
    print $process "From: ".$self->{'from'}."\n";
    print $process "To: ".$self->{'to'}."\n";
    print $process "Subject: ".$self->{'subject'}."\n";
    print $process "\n";
    print $process $self->{'body'};
    print $process "\n";
    close($process);
}

1;