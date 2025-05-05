Readme for python inted insertion finder. Run this script via the commandline with Python3.
You can also show the following message by running;
 - python3 BATCH-GE_INS_finder.py --help

Run insertion finder on zebrafish BATCH-GE pipeline output in case of intended insertion repair mutation. Retrieve intended mutation statistics and add them to the out_mail overview table.

options:
  -h, --help            show this help message and exit
  --repair_seq REPAIR_SEQ, -r REPAIR_SEQ
                        REQUIRED : Repair sequence only containing the intended insertion in brackets in the right
                        position
  --outmail_file OUTMAIL_FILE, -o OUTMAIL_FILE
                        REQUIRED : The output mail table to add the results to.
  --insertion_file INSERTION_FILE, -i INSERTION_FILE
                        REQUIRED : The Variants.INS.Seq.annotated.txt file to read the insertion data from.
						
						
Explanation:

BATCH-GE was build for detecting inteded mutations. Not for intended insertions. So when an insertion is the intended mutation, in the BATCH-GE repair sequence you can't denote it with the square brackets [ ].
If the BATCH-GE repair sequence is missing square brackets it will, after some filtering, mark every read as having the intended mutations. This is of course not the case.
This script will go trough a run's Variants.INS.Seq.annotated.txt output file to find the number of reads with the actual intended 
insertions and modify the outputmail.txt file's KI column to correctly show the number of reads with the intended mutation.

Example:

Outputmail before script:
Staalnaam	Totaal	Indel	KI
Kirsten-B2-14	2885	20	2723
Kirsten-B2-19	3150	1568	1506
Kirsten-D2-5	1254	0	1199
Kirsten-B2-3	20	0	19
Kirsten-B1-15	116	69	43
Kirsten-B1-20	1169	168	964
Kirsten-D1-2	1159	0	1114


Outputmail after script:
Staalnaam	Totaal	Indel	KI
Kirsten-B2-14	2885	20	0
Kirsten-B2-19	3150	1302	266
Kirsten-D2-5	1254	0	0
Kirsten-B2-3	20	0	0
Kirsten-B1-15	116	69	0
Kirsten-B1-20	1169	168	0
Kirsten-D1-2	1159	0	0

