# README #

This pipeline is a copy of the pipeline on bitbucket with additions about how to use this pipelines for human cells. 
based on the following: https://bitbucket.org/tychoCanterCremers/batch-ge_pipeline/src/master/
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

**Missense mutation = desired edit:**

Example:

Human (GRCh38.p13): CACNA1Cc.989C>T

* Transcript: CACNA1C-204 ENST00000347598.9

* Chr12 2493262 = mutation site

Adapted Batch-GE “zebrafish script”

* To provide:

	* Genome for reference

		* e.g.: “GRCh38/hg38”

* Genomic Region Of Interest (ROI) – unique per sgRNA

	* mutation site in the middle

	* add 10-15nt down- or upstream from cut site (related to sgRNA)

	* add 10-15nt down- or upstream from mutation site

	* Total length = max 40nt

	* e.g.: chr12 2493244 - 2493277

* Repair sequence:

	* [ ] = desired mutation; presence is required to count as knock-in (KI) read

	* ( ) = silent mutations; presence is optional to count as KI read

	* | = cut site (as example): CAGTGCCAGAA(T)G|G(A)A[T]GGTGTGCAAGCC

	* Final repair sequence e.g.: CAGTGCCAGAA(T)GG(A)A[T]GGTGTGCAAGCC

* When script is finished => output_mail.txt

	* Each row contains: Sample_ID; total_reads; InDel reads; KI reads

**Small insertion or deletion (max 50bp) = desired edit:**

After Batch_GE “zebrafish_script” use "Variants.INS.Seq.annotated" and "output_mail.txt" to run  cardio_zebrafishpipeline/BATCH-GE_intended_INS_finder/src/BATCH-GE_INS_finder.py, more information and a README about this script can be found in the same directory.