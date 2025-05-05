# README #

### What is this repository for? ###

* Genotyping SNPpanel using MIPS pipeline Nijmegen
* Input needed:
	* Illumins SampleSheet.csv: samples with "smMIPS" as Sample_project will be used, rest will be ignored
	* Design 70mer file 
	* SNPpanel in VCF format


### How do I set it up and run it? ###

* Copy 'localconfiguration.example.xml' to 'localconfiguration.xml' inside the repo and fill in the necessary info 
* Then run the main script: perl run.MIPS.pl
* Dependencies (defined in mainconfiguration.xml): 
	* bwa, cutadapt v.1.15, java, GATK v3.5, Python3.6 (installed on cluster)
* Set up to run as cronjob
