'''
Created on 10 Oct 2019

@author: tycho
'''

import argparse

def main(args):
    # repair_seq = "AGTCA[A]CACTA"[::-1]
    repair_seq = args.repair_seq[::-1]
    revcomp_repair_seq = ""
    complement_dict = {"A": "T", "T": "A", "G": "C", "C": "G", "[" : "]", "]" : "[", "(" : ")", ")" : "("}
    print(repair_seq)
    for base in repair_seq:
        if base in complement_dict:
            revcomp_repair_seq += complement_dict[base]
    print()
    intended_kis = {}
    # with open("../data/230320_M00984_0451_000000000-DJ6PR_results_gRNA1/Variants.INS.Seq.annotated.txt", "r") as variants:
    with open(args.insertion_file, "r") as variants:
        sample = ""
        for line in variants:
            line = line.split()
            if line == []:
                sample = ""
            if line != []:
                if line[0] == "Sample":
                    sample = line[2]
                else:
                    if line[0].startswith("chr"):
                        if revcomp_repair_seq in line[4]:
                            absolutefreq = line[5]
                            intended_kis[sample] = str(int(int(line[5]) / 2))
                            print(sample)
                            print(line[5])
    # with open("../data/230320_M00984_0451_000000000-DJ6PR_results_gRNA2/output_mail.txt", "r") as in_mail:
    with open(args.outmail_file, "r") as in_mail:
        # with open("../data/230320_M00984_0451_000000000-DJ6PR_results_gRNA2/output_mail_KI_indels.txt", "w") as out_mail:
        with open("/".join(args.outmail_file.split("/")[:-1]) + "/output_mail_KI_indels.txt", "w") as out_mail:
            for line in in_mail:
                line = line.strip().split("\t")
                if line[0] == "Staalnaam":
                    # line.append("KI_indel")
                    out_mail.write("\t".join(line))
                    out_mail.write("\n")
                else:
                    if line[0] in intended_kis:
                        line[2] = str(int(line[2]) - int(intended_kis[line[0]]))
                        line[3] = intended_kis[line[0]]
                        # line.append(intended_kis[line[0]])
                        out_mail.write("\t".join(line))
                        out_mail.write("\n")
                    else:
                        line[3] = "0"
                        out_mail.write("\t".join(line))
                        out_mail.write("\n")







if __name__ == "__main__":
    # parse and validate arguments.
    parser = argparse.ArgumentParser(
        description="Run insertion finder on zebrafish BATCH-GE pipeline output in case of intended insertion repair mutation. Retrieve intended mutation statistics and add them to the out_mail overview table.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="Notes : \n - Test please ignore")
    parser.add_argument("--repair_seq", "-r", type=str, help="REQUIRED : Repair sequence only containing the intended insertion in brackets in the right position",
                        required=True)
    parser.add_argument("--outmail_file", "-o", type=str, help="REQUIRED : The output mail table to add the results to.", required=True)
    parser.add_argument("--insertion_file", "-i", type=str, help="REQUIRED : The Variants.INS.Seq.annotated.txt file to read the insertion data from.", required=True)
    args = parser.parse_args()
    main(args)