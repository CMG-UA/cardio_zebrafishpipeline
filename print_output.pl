        # print to file
        open my $outputfile, '>', catfile($rundir, "output_mail.txt");
        print $outputfile join("\r\n", @output);
        close $outputfile;


