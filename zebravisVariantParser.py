'''
Created on 20 Jan 2021

@author: tycho
'''
import argparse
import re
import os

def main(args):
#     args.runDir = r"../data/"
    with open(args.runDir + "/job_results/Variants.txt", "r") as variant_file:
        with open(args.runDir + "/job_results/Variants.INS.Seq.annotated.txt", "w") as out_file:
            sample_id = ""
            for line in variant_file:
                line = line.rstrip()
                #Grab sample_id first
                if line.startswith("\tSample number"):
                    sample_id = line.split(" ")[2]
                    print(sample_id)
                    sample_read_dict = {}
                    #Parse sample samfile and store sam_read-sequence combinations in dictionary for easy variant lookup
                    with open(args.runDir + "/job_results/5_CutSiteReads/" + sample_id + "_R1_" + args.roi + ".sorted.remdup.rg.sam") as sample_sam_file:
                        for sam_read in sample_sam_file:
                            sam_read = sam_read.rstrip()
                            if not sam_read.startswith("@"):
                                sam_read = sam_read.split("\t")
                                sample_read_dict[sam_read[0]] = [sam_read[5],sam_read[9]]
                #Start parsing vairants
                if line.startswith("chr"):
                    variant = line.split("\t")
                    indel_type = variant[2]
                    #check length of variant array to make sure it contains all the info we need (e.g. has reads annotated in the last field).
                    if indel_type == "INS" and len(variant) == 9:
                        sam_read_data = sample_read_dict[variant[8]]
                        cigar = sam_read_data[0]
                        read_sequence = sam_read_data[1]
                        #Use sam CIGAR sequence to extract insertion position on the read. Insertion should directly follow the first Matching sequence.
                        if re.search(r"[0-9]+M[0-9]+I",cigar):
                            M_cigar_idx = int(re.search(r"[0-9]+M",cigar).group(0)[:-1])
                            I_cigar_idx = int(re.search(r"[0-9]+I",cigar).group(0)[:-1])
                            insert_sequence = read_sequence[M_cigar_idx:M_cigar_idx+I_cigar_idx]
                        flanking_sequence = variant[4].split("[]")
                        #Put INS sequence inside the variantfile sequence entry.
                        variant[4] = flanking_sequence[0] + "[" + insert_sequence + "]" + flanking_sequence[1]
                        out_file.write("\t".join(variant) + "\n")
                    else:
                        out_file.write(line + "\n")
                else:
                    out_file.write(line + "\n")
                                    
                
                                    
            


if __name__ == "__main__":
        # parse and validate arguments.
        parser = argparse.ArgumentParser(
                        description="Attempt to extract the insertion sequence from INS variants in the BATCH-GE Variant.txt output file",
                        formatter_class=argparse.RawDescriptionHelpFormatter)
        parser.add_argument("--runDir", "-r", type=str, help="REQUIRED : Input zebrafish pipeline run directory", required = False)
        parser.add_argument("--roi", "-i", type=str, help="REQUIRED : Region Of Interest (ROI) name", required=False)
        args = parser.parse_args()
        main(args)