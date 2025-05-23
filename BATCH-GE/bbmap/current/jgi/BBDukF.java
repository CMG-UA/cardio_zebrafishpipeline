package jgi;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLongArray;

import kmer.AbstractKmerTable;

import stream.ConcurrentReadInputStream;
import stream.FASTQ;
import stream.FastaReadInputStream;
import stream.ConcurrentReadOutputStream;
import stream.KillSwitch;
import stream.Read;
import stream.SamLine;

import align2.IntList;
import align2.ListNum;
import align2.ReadStats;
import align2.Shared;
import align2.Tools;
import align2.TrimRead;
import dna.AminoAcid;
import dna.Data;
import dna.Parser;
import dna.Timer;
import fileIO.ByteFile;
import fileIO.ByteStreamWriter;
import fileIO.ReadWrite;
import fileIO.FileFormat;
import fileIO.TextStreamWriter;

/**
 * Separates, trims, or masks sequences based on matching kmers in a reference.
 * Supports Hamming and and edit distance. 
 * Supports K 1-31 and emulated K>31.
 * @author Brian Bushnell
 * @date Aug 30, 2013
 *
 */
public class BBDukF {
	
	/*--------------------------------------------------------------*/
	/*----------------        Initialization        ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Code entrance from the command line.
	 * @param args Command line arguments
	 */
	public static void main(String[] args){
		
		args=Parser.parseConfig(args);
		if(Parser.parseHelp(args, true)){
			printOptions();
			System.exit(0);
		}
		
		//Create a new BBDuk instance
		BBDukF bbd=new BBDukF(args);
		
		//And run it
		bbd.process();
	}
	
	/**
	 * Display usage information.
	 */
	private static void printOptions(){
		System.err.println("Please consult the shellscript for usage information.");
	}
	
	
	/**
	 * Constructor.
	 * @param args Command line arguments
	 */
	public BBDukF(String[] args){
		for(String s : args){if(s.contains("standardout") || s.contains("stdout")){outstream=System.err;}}
		System.err.println("Executing "+getClass().getName()+" "+Arrays.toString(args)+"\n");
		System.err.println("BBDuk version "+Shared.BBMAP_VERSION_STRING);
		
		/* Set global defaults */
		ReadWrite.ZIPLEVEL=2;
		ReadWrite.USE_UNPIGZ=true;
		ReadWrite.USE_PIGZ=true;
		ReadWrite.MAX_ZIP_THREADS=8;
		
		
		ByteFile.FORCE_MODE_BF2=Shared.threads()>2;
		
		/* Initialize local variables with defaults */
		boolean setOut=false, setOutb=false;
		boolean ktrimRight_=false, ktrimLeft_=false, ktrimN_=false, ktrimExclusive_=false;
		boolean findBestMatch_=false;
		boolean addTrimmedToBad_=true;
		boolean rcomp_=true;
		boolean forbidNs_=false;
		boolean useForest_=false, useTable_=false, useArray_=true, prealloc_=false;
		int k_=27, kbig_=-1;
		int mink_=-1;
		int ways_=-1; //Currently disabled
		int maxBadKmers_=0;
		long skipreads_=0;
		byte TRIM_SYMBOL_='N';
		boolean kmaskLowercase_=false;
		boolean kmaskFullyCovered_=false;
		
		
		Parser parser=new Parser();
		parser.trimq=6;
		parser.minAvgQuality=0;
		parser.minReadLength=10;
		parser.maxReadLength=Integer.MAX_VALUE;
		parser.minLenFraction=0f;
		parser.requireBothBad=false;
		parser.maxNs=-1;
		boolean trimByOverlap_=false, useQualityForOverlap_=false, strictOverlap_=true;
		boolean trimPairsEvenly_=false;
		boolean ordered_=false;
		int minoverlap_=-1, mininsert_=-1;
		int restrictLeft_=0, restrictRight_=0, speed_=0, qSkip_=1;
		boolean printNonZeroOnly_=true;
		boolean rename_=false, useRefNames_=false;
		boolean skipr1_=false, skipr2_=false;
		boolean ecc_=false;
		float minBaseFrequency_=0;
		float minKmerFraction_=0;
		float minCoveredFraction_=0;
		
		scaffoldNames.add(""); //Necessary so that the first real scaffold gets an id of 1, not zero
		scaffoldLengths.add(0);
		
		{
			boolean b=false;
			assert(b=true);
			EA=b;
		}
		
		/* Parse arguments */
		for(int i=0; i<args.length; i++){

			final String arg=args[i];
			String[] split=arg.split("=");
			String a=split[0].toLowerCase();
			String b=split.length>1 ? split[1] : null;
			if("null".equalsIgnoreCase(b)){b=null;}
			while(a.charAt(0)=='-' && (a.indexOf('.')<0 || i>1 || !new File(a).exists())){a=a.substring(1);}
			
			if(Parser.isJavaFlag(arg)){
				//jvm argument; do nothing
			}else if(Parser.parseZip(arg, a, b)){
				//do nothing
			}else if(Parser.parseHist(arg, a, b)){
				//do nothing
			}else if(Parser.parseCommonStatic(arg, a, b)){
				//do nothing
			}else if(Parser.parseQualityAdjust(arg, a, b)){
				//do nothing
			}else if(Parser.parseQuality(arg, a, b)){
				//do nothing
			}else if(Parser.parseFasta(arg, a, b)){
				//do nothing
			}else if(parser.parseInterleaved(arg, a, b)){
				//do nothing
			}else if(parser.parseTrim(arg, a, b)){
				//do nothing
			}else if(parser.parseCommon(arg, a, b)){
				//do nothing
			}else if(parser.parseCardinality(arg, a, b)){
				//do nothing
			}else if(a.equals("in") || a.equals("in1")){
				in1=b;
			}else if(a.equals("in2")){
				in2=b;
			}else if(a.equals("qfin") || a.equals("qfin1")){
				qfin1=b;
			}else if(a.equals("qfin2")){
				qfin2=b;
			}else if(a.equals("out") || a.equals("out1") || a.equals("outu") || a.equals("outu1") || a.equals("outnonmatch") || 
					a.equals("outnonmatch1") || a.equals("outunnmatch") || a.equals("outunmatch1") || a.equals("outunnmatched") || a.equals("outunmatched1")){
				out1=b;
				setOut=true;
			}else if(a.equals("out2") || a.equals("outu2") || a.equals("outnonmatch2") || a.equals("outunmatch2") || 
					a.equals("outnonmatched2") || a.equals("outunmatched2")){
				out2=b;
			}else if(a.equals("outb") || a.equals("outm") || a.equals("outb1") || a.equals("outm1") || a.equals("outbad") || 
					a.equals("outbad1") || a.equals("outmatch") || a.equals("outmatch1")){
				outb1=b;
				setOut=true;
			}else if(a.equals("outb2") || a.equals("outm2") || a.equals("outbad2") || a.equals("outmatch2")){
				outb2=b;
			}else if(a.equals("outs") || a.equals("outsingle")){
				outsingle=b;
			}else if(a.equals("stats") || a.equals("scafstats")){
				outstats=b;
			}else if(a.equals("refstats")){
				outrefstats=b;
			}else if(a.equals("rpkm") || a.equals("fpkm") || a.equals("cov") || a.equals("coverage")){
				outrpkm=b;
			}else if(a.equals("sam") || a.equals("bam")){
				samFile=b;
			}else if(a.equals("duk") || a.equals("outduk")){
				outduk=b;
			}else if(a.equals("rqc")){
				outrqc=b;
			}else if(a.equals("ref")){
				ref=(b==null) ? null : (new File(b).exists() ? new String[] {b} : b.split(","));
			}else if(a.equals("literal")){
				literal=(b==null) ? null : b.split(",");
//				assert(false) : b+", "+Arrays.toString(literal);
			}else if(a.equals("forest")){
				useForest_=Tools.parseBoolean(b);
				if(useForest_){useTable_=useArray_=false;}
			}else if(a.equals("table")){
				useTable_=Tools.parseBoolean(b);
				if(useTable_){useForest_=useArray_=false;}
			}else if(a.equals("array")){
				useArray_=Tools.parseBoolean(b);
				if(useArray_){useTable_=useForest_=false;}
			}else if(a.equals("ways")){
				ways_=Integer.parseInt(b);
			}else if(a.equals("ordered") || a.equals("ord")){
				ordered_=Tools.parseBoolean(b);
				System.err.println("Set ORDERED to "+ordered_);
			}else if(a.equals("skipr1")){
				skipr1_=Tools.parseBoolean(b);
			}else if(a.equals("skipr2")){
				skipr2_=Tools.parseBoolean(b);
			}else if(a.equals("k")){
				assert(b!=null) : "\nThe k key needs an integer value greater than 0, such as k=27\n";
				k_=Integer.parseInt(b);
				if(k_>31){
					kbig_=k_;
					k_=31;
				}else{
					kbig_=-1;
				}
				assert(k_>0 && k_<32) : "k must be at least 1; default is 27.";
			}else if(a.equals("mink") || a.equals("kmin")){
				mink_=Integer.parseInt(b);
				assert(mink_<0 || (mink_>0 && mink_<32)) : "kmin must be between 1 and 31; default is 4, negative numbers disable it.";
			}else if(a.equals("useshortkmers") || a.equals("shortkmers") || a.equals("usk")){
				useShortKmers=Tools.parseBoolean(b);
			}else if(a.equals("trimextra") || a.equals("trimpad") || a.equals("tp")){
				trimPad=Integer.parseInt(b);
			}else if(a.equals("hdist") || a.equals("hammingdistance")){
				hammingDistance=Integer.parseInt(b);
				assert(hammingDistance>=0 && hammingDistance<4) : "hamming distance must be between 0 and 3; default is 0.";
			}else if(a.equals("qhdist") || a.equals("queryhammingdistance")){
				qHammingDistance=Integer.parseInt(b);
				assert(qHammingDistance>=0 && qHammingDistance<4) : "hamming distance must be between 0 and 3; default is 0.";
			}else if(a.equals("edits") || a.equals("edist") || a.equals("editdistance")){
				editDistance=Integer.parseInt(b);
				assert(editDistance>=0 && editDistance<3) : "edit distance must be between 0 and 2; default is 0.\n" +
						"You can bypass this error message with the -da flag, but edist=3 at K=31" +
						"requires 15,000,000x the time and memory for indexing compared to edist=0.";
			}else if(a.equals("hdist2") || a.equals("hammingdistance2")){
				hammingDistance2=Integer.parseInt(b);
				assert(hammingDistance2>=0 && hammingDistance2<4) : "hamming distance must be between 0 and 3; default is 0.";
			}else if(a.equals("qhdist2") || a.equals("queryhammingdistance2")){
				qHammingDistance2=Integer.parseInt(b);
				assert(qHammingDistance2>=0 && qHammingDistance2<4) : "hamming distance must be between 0 and 3; default is 0.";
			}else if(a.equals("edits2") || a.equals("edist2") || a.equals("editdistance2")){
				editDistance2=Integer.parseInt(b);
				assert(editDistance2>=0 && editDistance2<3) : "edit distance must be between 0 and 2; default is 0.";
			}else if(a.equals("maxskip") || a.equals("maxrskip") || a.equals("mxs")){
				maxSkip=Integer.parseInt(b);
			}else if(a.equals("minskip") || a.equals("minrskip") || a.equals("mns")){
				minSkip=Integer.parseInt(b);
			}else if(a.equals("skip") || a.equals("refskip") || a.equals("rskip")){
				minSkip=maxSkip=Integer.parseInt(b);
			}else if(a.equals("qskip")){
				qSkip_=Integer.parseInt(b);
			}else if(a.equals("speed")){
				speed_=Integer.parseInt(b);
				assert(speed_>=0 && speed_<=15) : "Speed range is 0 to 15.  Value: "+speed_;
			}else if(a.equals("skipreads")){
				skipreads_=Tools.parseKMG(b);
			}else if(a.equals("maxbadkmers") || a.equals("mbk")){
				maxBadKmers_=Integer.parseInt(b);
			}else if(a.equals("minhits") || a.equals("minkmerhits") || a.equals("mkh")){
				maxBadKmers_=Integer.parseInt(b)-1;
			}else if(a.equals("minkmerfraction") || a.equals("minfraction") || a.equals("mkf")){
				minKmerFraction_=Float.parseFloat(b);
			}else if(a.equals("mincoveredfraction") || a.equals("mincovfraction") || a.equals("mcf")){
				minCoveredFraction_=Float.parseFloat(b);
			}else if(a.equals("showspeed") || a.equals("ss")){
				showSpeed=Tools.parseBoolean(b);
			}else if(a.equals("verbose")){
				assert(false) : "Verbose flag is currently static final; must be recompiled to change.";
				assert(WAYS>1) : "WAYS=1 is for debug mode.";
//				verbose=Tools.parseBoolean(b); //123
				if(verbose){outstream=System.err;} //For some reason System.out does not print in verbose mode.
			}else if(a.equals("mm") || a.equals("maskmiddle")){
				maskMiddle=Tools.parseBoolean(b);
			}else if(a.equals("rcomp")){
				rcomp_=Tools.parseBoolean(b);
			}else if(a.equals("forbidns") || a.equals("forbidn") || a.equals("fn")){
				forbidNs_=Tools.parseBoolean(b);
			}else if(a.equals("findbestmatch") || a.equals("fbm")){
				findBestMatch_=Tools.parseBoolean(b);
			}else if(a.equals("kfilter")){
				boolean x=Tools.parseBoolean(b);
				if(x){ktrimLeft_=ktrimRight_=ktrimN_=false;}
			}else if(a.equals("ktrim")){
				if(b==null){b="";}
				if(b.equalsIgnoreCase("left") || b.equalsIgnoreCase("l")){ktrimLeft_=true;ktrimRight_=false;ktrimN_=false;}
				else if(b.equalsIgnoreCase("right") || b.equalsIgnoreCase("r")){ktrimLeft_=false;ktrimRight_=true;ktrimN_=false;}
				else if(b.equalsIgnoreCase("n")){ktrimLeft_=false;ktrimRight_=false;ktrimN_=true;}
				else if(b.length()==1 && !b.equalsIgnoreCase("t") && !b.equalsIgnoreCase("f")){
					ktrimLeft_=false;ktrimRight_=false;ktrimN_=true;
					TRIM_SYMBOL_=(byte)b.charAt(0);
				}else{
					boolean x=Tools.parseBoolean(b);
					assert(!x) : "\nInvalid setting for ktrim - values must be f (false), l (left), r (right), or n.";
					ktrimRight_=ktrimLeft_=ktrimN_=x;
				}
			}else if(a.equals("kmask") || a.equals("mask")){
				if("lc".equalsIgnoreCase(b) || "lowercase".equalsIgnoreCase(b)){
					kmaskLowercase_=true;
					ktrimLeft_=false;ktrimRight_=false;ktrimN_=true;
				}else{
					if(Tools.parseBoolean(b)){b="N";}
					if(b.length()==1 && !b.equalsIgnoreCase("f")){
						ktrimLeft_=false;ktrimRight_=false;ktrimN_=true;
						TRIM_SYMBOL_=(byte)b.charAt(0);
					}else{
						boolean x=Tools.parseBoolean(b);
						assert(!x) : "\nInvalid setting for kmask - values must be f (false), t (true), or a single character for replacement.";
						ktrimRight_=ktrimLeft_=ktrimN_=x;
					}
				}
			}else if(a.equals("kmaskfullycovered") || a.equals("maskfullycovered") || a.equals("mfc")){
				kmaskFullyCovered_=Tools.parseBoolean(b);
			}else if(a.equals("ktrimright")){
				ktrimRight_=Tools.parseBoolean(b);
				ktrimLeft_=ktrimN_=!(ktrimRight_);
			}else if(a.equals("ktrimleft")){
				ktrimLeft_=Tools.parseBoolean(b);
				ktrimRight_=ktrimN_=!(ktrimLeft_);
			}else if(a.equals("ktrimn")){
				ktrimN_=Tools.parseBoolean(b);
				ktrimLeft_=ktrimRight_=!(ktrimN_);
			}else if(a.equals("ktrimexclusive")){
				ktrimExclusive_=Tools.parseBoolean(b);
			}else if(a.equals("tbo") || a.equals("trimbyoverlap")){
				trimByOverlap_=Tools.parseBoolean(b);
			}else if(a.equals("strictoverlap")){
				strictOverlap_=Tools.parseBoolean(b);
			}else if(a.equals("usequality")){
				useQualityForOverlap_=Tools.parseBoolean(b);
			}else if(a.equals("tpe") || a.equals("tbe") || a.equals("trimpairsevenly")){
				trimPairsEvenly_=Tools.parseBoolean(b);
			}else if(a.equals("ottm") || a.equals("outputtrimmedtomatch")){
				addTrimmedToBad_=Tools.parseBoolean(b);
			}else if(a.equals("minoverlap")){
				minoverlap_=Integer.parseInt(b);
			}else if(a.equals("mininsert")){
				mininsert_=Integer.parseInt(b);
			}else if(a.equals("prealloc") || a.equals("preallocate")){
				if(b==null || b.length()<1 || Character.isLetter(b.charAt(0))){
					prealloc_=Tools.parseBoolean(b);
				}else{
					preallocFraction=Tools.max(0, Double.parseDouble(b));
					prealloc_=(preallocFraction>0);
				}
			}else if(a.equals("restrictleft")){
				restrictLeft_=Integer.parseInt(b);
			}else if(a.equals("restrictright")){
				restrictRight_=Integer.parseInt(b);
			}else if(a.equals("statscolumns") || a.equals("columns") || a.equals("cols")){
				STATS_COLUMNS=Integer.parseInt(b);
				assert(STATS_COLUMNS==3 || STATS_COLUMNS==5) : "statscolumns bust be either 3 or 5. Invalid value: "+STATS_COLUMNS;
			}else if(a.equals("nzo") || a.equals("nonzeroonly")){
				printNonZeroOnly_=Tools.parseBoolean(b);
			}else if(a.equals("rename")){
				rename_=Tools.parseBoolean(b);
			}else if(a.equals("refnames") || a.equals("userefnames")){
				useRefNames_=Tools.parseBoolean(b);
			}else if(a.equals("initialsize")){
				initialSize=(int)Tools.parseKMG(b);
			}else if(a.equals("dump")){
				dump=b;
			}else if(a.equals("entropyk") || a.equals("ek")){
				entropyK=Integer.parseInt(b);
			}else if(a.equals("entropywindow") || a.equals("ew")){
				entropyWindow=Integer.parseInt(b);
			}else if(a.equals("minentropy") || a.equals("entropy") || a.equals("entropyfilter")){
				entropyCutoff=Float.parseFloat(b);
			}else if(a.equals("verifyentropy")){
				verifyEntropy=Tools.parseBoolean(b);
			}else if(a.equals("minbasefrequency")){
				minBaseFrequency_=Float.parseFloat(b);
			}else if(a.equals("ecco") || a.equals("ecc")){
				ecc_=Tools.parseBoolean(b);
			}else if(a.equals("copyundefined") || a.equals("cu")){
				REPLICATE_AMBIGUOUS=Tools.parseBoolean(b);
			}else if(a.equals("path")){
				Data.setPath(b);
			}else if(a.equals("maxbasesoutm")){
				maxBasesOutm=Tools.parseKMG(b);
			}else if(a.equals("maxbasesoutu") || a.equals("maxbasesout")){
				maxBasesOutu=Tools.parseKMG(b);
			}else if(i==0 && in1==null && arg.indexOf('=')<0 && arg.lastIndexOf('.')>0){
				in1=args[i];
			}else if(i==1 && out1==null && arg.indexOf('=')<0 && arg.lastIndexOf('.')>0){
				out1=args[i];
				setOut=true;
			}else if(i==2 && ref==null && arg.indexOf('=')<0 && arg.lastIndexOf('.')>0){
				ref=(new File(args[i]).exists() ? new String[] {args[i]} : args[i].split(","));
			}else{
				throw new RuntimeException("Unknown parameter "+args[i]);
			}
		}
		
		if(hammingDistance2==-1){hammingDistance2=hammingDistance;}
		if(qHammingDistance2==-1){qHammingDistance2=qHammingDistance;}
		if(editDistance2==-1){editDistance2=editDistance;}
		minBaseFrequency=minBaseFrequency_;
		
		{//Process parser fields
			Parser.processQuality();
			
			maxReads=parser.maxReads;
			samplerate=parser.samplerate;
			sampleseed=parser.sampleseed;
			recalibrateQuality=parser.recalibrateQuality;
			
			overwrite=ReadStats.overwrite=parser.overwrite;
			append=ReadStats.append=parser.append;
//			testsize=parser.testsize;
//			trimBadSequence=parser.trimBadSequence;
//			breakLength=parser.breakLength;
			
			forceTrimModulo=parser.forceTrimModulo;
			forceTrimLeft=parser.forceTrimLeft;
			forceTrimRight=parser.forceTrimRight;
			forceTrimRight2=parser.forceTrimRight2;
			qtrimLeft=parser.qtrimLeft;
			qtrimRight=parser.qtrimRight;
			trimq=parser.trimq;
			minLenFraction=parser.minLenFraction;
			minAvgQuality=parser.minAvgQuality;
			minAvgQualityBases=parser.minAvgQualityBases;
			chastityFilter=parser.chastityFilter;
			failBadBarcodes=parser.failBadBarcodes;
			removeBadBarcodes=parser.removeBadBarcodes;
			failIfNoBarcode=parser.failIfNoBarcode;
			barcodes=parser.barcodes;
			minReadLength=parser.minReadLength;
			maxReadLength=parser.maxReadLength;
			maxNs=parser.maxNs;
			minConsecutiveBases=parser.minConsecutiveBases;
//			untrim=parser.untrim;
//			minTrimLength=(parser.minTrimLength>=0 ? parser.minTrimLength : minTrimLength);
//			requireBothBad=parser.requireBothBad;
			removePairsIfEitherBad=!parser.requireBothBad;

			minGC=parser.minGC;
			maxGC=parser.maxGC;
			filterGC=(minGC>0 || maxGC<1);
			usePairGC=parser.usePairGC;

			loglog=(parser.loglog ? new LogLog(parser) : null);
			
			THREADS=Shared.threads();
		}
		
		if(ref!=null){
			for(String s : ref){refNames.add(s);}
		}
		if(literal!=null){refNames.add("literal");}
		refScafCounts=new int[refNames.size()];
		
		if(minoverlap_>=0){
			minOverlap=Tools.max(minoverlap_, 1);
			minOverlap0=Tools.min(minOverlap0, minOverlap);
		}
		
		if(mininsert_>=0){
			minInsert=Tools.max(mininsert_, 1);
			minInsert0=Tools.min(minInsert0, minInsert);
		}
		
		/* Set final variables; post-process and validate argument combinations */
		
		useForest=useForest_;
		useTable=useTable_;
		useArray=useArray_;
		hammingDistance=Tools.max(editDistance, hammingDistance);
		hammingDistance2=Tools.max(editDistance2, hammingDistance2);
		minSkip=Tools.max(1, Tools.min(minSkip, maxSkip));
		maxSkip=Tools.max(minSkip, maxSkip);
		addTrimmedToBad=addTrimmedToBad_;
		rcomp=rcomp_;
		forbidNs=(forbidNs_ || hammingDistance<1);
		trimSymbol=TRIM_SYMBOL_;
		kmaskLowercase=kmaskLowercase_;
		kmaskFullyCovered=kmaskFullyCovered_;
		skipreads=skipreads_;
		trimByOverlap=trimByOverlap_;
		useQualityForOverlap=useQualityForOverlap_;
		strictOverlap=strictOverlap_;
		trimPairsEvenly=trimPairsEvenly_;
		ORDERED=ordered_;
		restrictLeft=Tools.max(restrictLeft_, 0);
		restrictRight=Tools.max(restrictRight_, 0);
		printNonZeroOnly=printNonZeroOnly_;
		rename=rename_;
		useRefNames=useRefNames_;
		speed=speed_;
		qSkip=qSkip_;
		noAccel=(speed<1 && qSkip<2);
		accel=!noAccel;
		skipR1=skipr1_;
		skipR2=skipr2_;
		ecc=ecc_;
		
		if(strictOverlap){
			maxRatio=0.05f;
			ratioMargin=9f;
			ratioOffset=0.5f;
			efilterRatio=3.5f;
			efilterOffset=0.05f;
			pfilterRatio=0.001f;
			meeFilter=15f;
		}else{
			maxRatio=0.10f;
			ratioMargin=5f;
			ratioOffset=0.4f;
			efilterRatio=6f;
			efilterOffset=0.05f;
			pfilterRatio=0.00005f;
			meeFilter=999999999;
		}
		
		MAKE_QUALITY_HISTOGRAM=ReadStats.COLLECT_QUALITY_STATS;
		MAKE_QUALITY_ACCURACY=ReadStats.COLLECT_QUALITY_ACCURACY;
		MAKE_MATCH_HISTOGRAM=ReadStats.COLLECT_MATCH_STATS;
		MAKE_BASE_HISTOGRAM=ReadStats.COLLECT_BASE_STATS;
		MAKE_EHIST=ReadStats.COLLECT_ERROR_STATS;
		MAKE_INDELHIST=ReadStats.COLLECT_INDEL_STATS;
		MAKE_LHIST=ReadStats.COLLECT_LENGTH_STATS;
		MAKE_GCHIST=ReadStats.COLLECT_GC_STATS;
		MAKE_IDHIST=ReadStats.COLLECT_IDENTITY_STATS;
		
		{
			long usableMemory;
			long tableMemory;

			{
				long memory=Runtime.getRuntime().maxMemory();
				double xmsRatio=Shared.xmsRatio();
				usableMemory=(long)Tools.max(((memory-96000000-(20*400000 /* for atomic arrays */))*(xmsRatio>0.97 ? 0.82 : 0.75)), memory*0.45);
				tableMemory=(long)(usableMemory*.95);
			}

			if(initialSize<1){
				final long memOverWays=tableMemory/(12*WAYS);
				final double mem2=(prealloc_ ? preallocFraction : 1)*tableMemory;
				initialSize=(prealloc_ || memOverWays<initialSizeDefault ? (int)Tools.min(2142000000, (long)(mem2/(12*WAYS))) : initialSizeDefault);
				if(initialSize!=initialSizeDefault){
					System.err.println("Initial size set to "+initialSize);
				}
			}
		}
		
		if(ktrimLeft_ || ktrimRight_ || ktrimN_){
			if(kbig_>k_){
				System.err.println("***********************   WARNING   ***********************"); 
				System.err.println("WARNING: When kmer-trimming, the maximum value of K is "+k_+".");
				System.err.println("K has been reduced from "+kbig_+" to "+k_+".");
				System.err.println("***********************************************************"); 
				kbig_=k_;
			}
		}
		
		if((speed>0 || qSkip>1) && kbig_>k_){
			System.err.println("***********************   WARNING   ***********************"); 
			System.err.println("WARNING: When speed>0 or qskip>1, the maximum value of K is "+k_+".");
			System.err.println("K has been reduced from "+kbig_+" to "+k_+".");
			System.err.println("***********************************************************"); 
			kbig_=k_;
		}
		
		if((speed>0 && qSkip>1) || (qSkip>1 && maxSkip>1) || (speed>0 && maxSkip>1)){
			System.err.println("WARNING: It is not recommended to use more than one of 'qskip', 'speed', and 'rskip/maxskip' together.");
			System.err.println("qskip="+qSkip+", speed="+speed+", maxskip="+maxSkip);
		}
		
		k=k_;
		k2=k-1;
		kbig=kbig_;
		keff=Tools.max(k, kbig);
		if(kbig>k){
			minSkip=maxSkip=0;
			if(maskMiddle){
				System.err.println("maskMiddle was disabled because kbig>k");
				maskMiddle=false;
			}
		}
		mink=Tools.min((mink_<1 ? 6 : mink_), k);
		maxBadKmers0=maxBadKmers_;

		minKmerFraction=Tools.max(minKmerFraction_, 0);
		assert(minKmerFraction<=1) : "minKmerFraction must range from 0 to 1; value="+minKmerFraction;
		
		minCoveredFraction=Tools.max(minCoveredFraction_, 0);
		assert(minCoveredFraction<=1) : "minCoveredFraction must range from 0 to 1; value="+minCoveredFraction;
		
		if(mink_>0 && mink_<k){useShortKmers=true;}
		if(useShortKmers){
			if(maskMiddle){
				System.err.println("maskMiddle was disabled because useShortKmers=true");
				maskMiddle=false;
			}
		}
		
		ktrimRight=ktrimRight_;
		ktrimLeft=ktrimLeft_;
		ktrimN=ktrimN_;
		ktrimExclusive=ktrimExclusive_;
		findBestMatch=findBestMatch_;
		kfilter=(ref!=null || literal!=null) && !(ktrimRight || ktrimLeft || ktrimN);
		assert(findBestMatch==false || kfilter==false || kbig<=k) : "K must be less than 32 in 'findBestMatch' mode";
		
		assert(!useShortKmers || ktrimRight || ktrimLeft || ktrimN) : "\nSetting mink or useShortKmers also requires setting a ktrim mode, such as 'r', 'l', or 'n'\n";
		
		middleMask=maskMiddle ? ~(3L<<(2*(k/2))) : -1L;
		
		hitCounts=(outduk==null ? null : new long[HITCOUNT_LEN+1]);
		
		
		/* Adjust I/O settings and filenames */
		
		assert(FastaReadInputStream.settingsOK());
		
		if(in1==null){
			printOptions();
			throw new RuntimeException("Error - at least one input file is required.");
		}
		
		if(in1!=null && in1.contains("#") && !new File(in1).exists()){
			int pound=in1.lastIndexOf('#');
			String a=in1.substring(0, pound);
			String b=in1.substring(pound+1);
			in1=a+1+b;
			in2=a+2+b;
		}
		if(in2!=null && (in2.contains("=") || in2.equalsIgnoreCase("null"))){in2=null;}
		if(in2!=null){
			if(FASTQ.FORCE_INTERLEAVED){System.err.println("Reset INTERLEAVED to false because paired input files were specified.");}
			FASTQ.FORCE_INTERLEAVED=FASTQ.TEST_INTERLEAVED=false;
		}
		
		if(qfin1!=null && qfin1.contains("#") && in2!=null && !new File(qfin1).exists()){
			int pound=qfin1.lastIndexOf('#');
			String a=qfin1.substring(0, pound);
			String b=qfin1.substring(pound+1);
			qfin1=a+1+b;
			qfin2=a+2+b;
		}
		
		if(out1!=null && out1.contains("#")){
			int pound=out1.lastIndexOf('#');
			String a=out1.substring(0, pound);
			String b=out1.substring(pound+1);
			out1=a+1+b;
			out2=a+2+b;
		}
		
		if(outb1!=null && outb1.contains("#")){
			int pound=outb1.lastIndexOf('#');
			String a=outb1.substring(0, pound);
			String b=outb1.substring(pound+1);
			outb1=a+1+b;
			outb2=a+2+b;
		}
		
		if((out2!=null || outb2!=null) && (in1!=null && in2==null)){
			if(!FASTQ.FORCE_INTERLEAVED){System.err.println("Forcing interleaved input because paired output was specified for a single input file.");}
			FASTQ.FORCE_INTERLEAVED=FASTQ.TEST_INTERLEAVED=true;
		}

		if(!setOut){
			System.err.println("No output stream specified.  To write to stdout, please specify 'out=stdout.fq' or similar.");
//			out1="stdout";
//			outstream=System.err;
//			out2=null;
			out1=out2=null;
		}else if("stdout".equalsIgnoreCase(out1) || "standarddout".equalsIgnoreCase(out1)){
			out1="stdout.fq";
			outstream=System.err;
			out2=null;
		}
		
		if(!Tools.testOutputFiles(overwrite, append, false, out1, out2, outb1, outb2, outsingle, outstats, outrpkm, outduk, outrqc, outrefstats)){
			throw new RuntimeException("\nCan't write to some output files; overwrite="+overwrite+"\n");
		}
		if(!Tools.testInputFiles(false, true, in1, in2, qfin1, qfin2)){
			throw new RuntimeException("\nCan't read to some input files.\n");
		}
		if(!Tools.testInputFiles(true, true, ref)){
			throw new RuntimeException("\nCan't read to some reference files.\n");
		}
		if(!Tools.testForDuplicateFiles(true, in1, in2, qfin1, qfin2, out1, out2, outb1, outb2, outsingle, outstats, outrpkm, outduk, outrqc, outrefstats)){
			throw new RuntimeException("\nSome file names were specified multiple times.\n");
		}
		
		assert(THREADS>0) : "THREADS must be greater than 0.";

		assert(in1==null || in1.toLowerCase().startsWith("stdin") || in1.toLowerCase().startsWith("standardin") || new File(in1).exists()) : "Can't find "+in1;
		assert(in2==null || in2.toLowerCase().startsWith("stdin") || in2.toLowerCase().startsWith("standardin") || new File(in2).exists()) : "Can't find "+in2;
		
		if(!((ref!=null || literal!=null) || qtrimLeft || qtrimRight || minAvgQuality>0 || maxNs>=0 || trimByOverlap ||
				MAKE_QUALITY_HISTOGRAM || MAKE_MATCH_HISTOGRAM || MAKE_BASE_HISTOGRAM || MAKE_QUALITY_ACCURACY || 
				MAKE_EHIST || MAKE_INDELHIST || MAKE_LHIST || MAKE_GCHIST || MAKE_IDHIST || 
				forceTrimLeft>0 || forceTrimRight>0 || forceTrimRight2>0 || forceTrimModulo>0 || minBaseFrequency>0 || recalibrateQuality)){
			System.err.println("NOTE: No reference files specified, no trimming mode, no min avg quality, no histograms - read sequences will not be changed.");
		}
		
		if(recalibrateQuality){
			SamLine.SET_FROM_OK=true;//TODO:  Should ban other operations
		}
				
		if(ref!=null){
			for(String s0 : ref){
				assert(s0!=null) : "Specified a null reference.";
				String s=s0.toLowerCase();
				assert(s==null || s.startsWith("stdin") || s.startsWith("standardin") || new File(s0).exists()) : "Can't find "+s0;
			}
		}
		
		//Initialize tables
		final int tableType=(useForest ? AbstractKmerTable.FOREST1D : useTable ? AbstractKmerTable.TABLE : useArray ? AbstractKmerTable.ARRAY1D : 0);
		keySets=AbstractKmerTable.preallocate(WAYS, tableType, initialSize, (!prealloc_ || preallocFraction<1));
		
		//Initialize entropy
		calcEntropy=(entropyCutoff>0);
		if(calcEntropy){
			assert(entropyWindow>0 && entropyCutoff>=0 && entropyCutoff<=1);
			entropy=new double[entropyWindow+2];
			final double mult=1d/entropyWindow;
			for(int i=0; i<entropy.length; i++){
				double pk=i*mult;
				entropy[i]=pk*Math.log(pk);
			}
			entropyMult=-1/Math.log(entropyWindow);
			entropyKmerspace=(1<<(2*entropyK));
		}else{
			entropy=null;
			entropyMult=0;
			entropyKmerspace=1;
		}
	}

	
	/*--------------------------------------------------------------*/
	/*----------------         Outer Methods        ----------------*/
	/*--------------------------------------------------------------*/
	
	
	public void process(){
		
		if(recalibrateQuality){
			if(samFile!=null){
				CalcTrueQuality.main2(new String[] {"in="+samFile, "showstats=f"});
			}
			CalcTrueQuality.initializeMatrices();
		}
		
		/* Check for output file collisions */
		if(!Tools.testOutputFiles(overwrite, append, false, out1, out2, outb1, outb2, outstats, outrpkm, outduk, outrqc, outrefstats)){
			throw new RuntimeException("One or more output files were duplicate or could not be written to.  Check the names or set the 'overwrite=true' flag.");
		}
		
		/* Start overall timer */
		Timer t=new Timer();
		
//		boolean dq0=FASTQ.DETECT_QUALITY;
//		boolean ti0=FASTQ.TEST_INTERLEAVED;
//		int rbl0=Shared.READ_BUFFER_LENGTH;
//		FASTQ.DETECT_QUALITY=false;
//		FASTQ.TEST_INTERLEAVED=false;
//		Shared.READ_BUFFER_LENGTH=16;
		
		process2(t.time1);
		
//		FASTQ.DETECT_QUALITY=dq0;
//		FASTQ.TEST_INTERLEAVED=ti0;
//		Shared.READ_BUFFER_LENGTH=rbl0;
		
		/* Stop timer and calculate speed statistics */
		t.stop();
		
		
		if(showSpeed){
			double rpnano=readsIn/(double)(t.elapsed);
			double bpnano=basesIn/(double)(t.elapsed);

			//Format with k or m suffixes
			String rpstring=(readsIn<100000 ? ""+readsIn : readsIn<100000000 ? (readsIn/1000)+"k" : (readsIn/1000000)+"m");
			String bpstring=(basesIn<100000 ? ""+basesIn : basesIn<100000000 ? (basesIn/1000)+"k" : (basesIn/1000000)+"m");

			while(rpstring.length()<8){rpstring=" "+rpstring;}
			while(bpstring.length()<8){bpstring=" "+bpstring;}

			outstream.println("\nTime:   \t\t\t"+t);
			outstream.println("Reads Processed:    "+rpstring+" \t"+String.format("%.2fk reads/sec", rpnano*1000000));
			outstream.println("Bases Processed:    "+bpstring+" \t"+String.format("%.2fm bases/sec", bpnano*1000));
		}
		
		/* Throw an exception if errors were detected */
		if(errorState){
			throw new RuntimeException(getClass().getName()+" terminated in an error state; the output may be corrupt.");
		}
	}
	
	
	public void process2(long startTime){
		
		/* Start phase timer */
		Timer t=new Timer();

		if(DISPLAY_PROGRESS){
			outstream.println("Initial:");
			Shared.printMemory();
			outstream.println();
		}
		
		/* Fill tables with reference kmers */
		if((ref!=null && ref.length>0) || (literal!=null && literal.length>0)){
			final boolean oldTI=FASTQ.TEST_INTERLEAVED; //TODO: This needs to be changed to a non-static field, or somehow 'read mode' and 'ref mode' need to be distinguished.
			final boolean oldFI=FASTQ.FORCE_INTERLEAVED;
			final boolean oldSplit=FastaReadInputStream.SPLIT_READS;
			final int oldML=FastaReadInputStream.MIN_READ_LEN;
			
			FASTQ.TEST_INTERLEAVED=false;
			FASTQ.FORCE_INTERLEAVED=false;
			FastaReadInputStream.SPLIT_READS=false;
			FastaReadInputStream.MIN_READ_LEN=1;
			
			storedKmers=spawnLoadThreads();
			
			FASTQ.TEST_INTERLEAVED=oldTI;
			FASTQ.FORCE_INTERLEAVED=oldFI;
			FastaReadInputStream.SPLIT_READS=oldSplit;
			FastaReadInputStream.MIN_READ_LEN=oldML;
			
			if(useRefNames){toRefNames();}
			t.stop();
		}
		
		{
			long ram=freeMemory();
			ALLOW_LOCAL_ARRAYS=(scaffoldNames!=null && Tools.max(THREADS, 1)*3*8*scaffoldNames.size()<ram*5);
		}
		
		/* Dump kmers to text */
		if(dump!=null){
			ByteStreamWriter bsw=new ByteStreamWriter(dump, overwrite, false, true);
			bsw.start();
			for(AbstractKmerTable set : keySets){
				set.dumpKmersAsBytes(bsw, k, 0);
			}
			bsw.poisonAndWait();
		}
		
		if(storedKmers<1 && (ktrimRight || ktrimLeft || ktrimN)){
			System.err.println("******  WARNING! A KMER OPERATION WAS CHOSEN BUT NO KMERS WERE LOADED.  ******");
			if(ref==null && literal==null){
				System.err.println("******  YOU NEED TO SPECIFY A REFERENCE FILE OR LITERAL SEQUENCE.       ******\n");
			}else{
				System.err.println("******  PLEASE ENSURE K IS LESS THAN OR EQUAL TO REF SEQUENCE LENGTHS.  ******\n");
			}
			assert(false) : "You can bypass this assertion with the -da flag.";
		}
		
		final boolean vic=Read.VALIDATE_IN_CONSTRUCTOR;
		Read.VALIDATE_IN_CONSTRUCTOR=THREADS<4;
		
		/* Do kmer matching of input reads */
		spawnProcessThreads(t);
		
		Read.VALIDATE_IN_CONSTRUCTOR=vic;
		
		/* Write legacy duk statistics (which requires tables) */
		writeDuk(System.nanoTime()-startTime);
		
		/* Unload kmers to save memory */
		if(RELEASE_TABLES){unloadKmers();}
		
		/* Write statistics to files */
		writeStats();
		writeRPKM();
		writeRefStats();
		writeRqc();
		
		/* Unload sequence data to save memory */
		if(RELEASE_TABLES){unloadScaffolds();}
		
		outstream.println("\nInput:                  \t"+readsIn+" reads \t\t"+basesIn+" bases.");
		
		if((ref!=null || literal!=null) && !(ktrimLeft || ktrimRight || ktrimN)){
			outstream.println("Contaminants:           \t"+readsKFiltered+" reads ("+String.format("%.2f",readsKFiltered*100.0/readsIn)+"%) \t"+
					basesKFiltered+" bases ("+String.format("%.2f",basesKFiltered*100.0/basesIn)+"%)");
			outstream.flush();
		}
		if(qtrimLeft || qtrimRight){
			outstream.println("QTrimmed:               \t"+readsQTrimmed+" reads ("+String.format("%.2f",readsQTrimmed*100.0/readsIn)+"%) \t"+
					basesQTrimmed+" bases ("+String.format("%.2f",basesQTrimmed*100.0/basesIn)+"%)");
		}
		if(forceTrimLeft>0 || forceTrimRight>0 || forceTrimRight2>0 || forceTrimModulo>0){
			outstream.println("FTrimmed:               \t"+readsFTrimmed+" reads ("+String.format("%.2f",readsFTrimmed*100.0/readsIn)+"%) \t"+
					basesFTrimmed+" bases ("+String.format("%.2f",basesFTrimmed*100.0/basesIn)+"%)");
		}
		if(ktrimLeft || ktrimRight || ktrimN){
			String x=(ktrimN ? "KMasked: " : "KTrimmed:");
			outstream.println(x+"               \t"+readsKTrimmed+" reads ("+String.format("%.2f",readsKTrimmed*100.0/readsIn)+"%) \t"+
					basesKTrimmed+" bases ("+String.format("%.2f",basesKTrimmed*100.0/basesIn)+"%)");
		}
		if(trimByOverlap){
			outstream.println("Trimmed by overlap:     \t"+readsTrimmedByOverlap+" reads ("+String.format("%.2f",readsTrimmedByOverlap*100.0/readsIn)+"%) \t"+
					basesTrimmedByOverlap+" bases ("+String.format("%.2f",basesTrimmedByOverlap*100.0/basesIn)+"%)");
		}
		if(filterGC){
			outstream.println("Filtered by GC:         \t"+badGcReads+" reads ("+String.format("%.2f",badGcReads*100.0/readsIn)+"%) \t"+
					badGcBases+" bases ("+String.format("%.2f",badGcBases*100.0/basesIn)+"%)");
		}
		if(minAvgQuality>0 || maxNs>=0 || minBaseFrequency>0 || chastityFilter || removeBadBarcodes){
			outstream.println("Low quality discards:   \t"+readsQFiltered+" reads ("+String.format("%.2f",readsQFiltered*100.0/readsIn)+"%) \t"+
					basesQFiltered+" bases ("+String.format("%.2f",basesQFiltered*100.0/basesIn)+"%)");
		}
		if(calcEntropy){
			outstream.println("Low entropy discards:   \t"+readsEFiltered+" reads ("+String.format("%.2f",readsEFiltered*100.0/readsIn)+"%) \t"+
					basesEFiltered+" bases ("+String.format("%.2f",basesEFiltered*100.0/basesIn)+"%)");
		}
		
		outstream.println("Result:                 \t"+readsOut+" reads ("+String.format("%.2f",readsOut*100.0/readsIn)+"%) \t"+
				basesOut+" bases ("+String.format("%.2f",basesOut*100.0/basesIn)+"%)");
		
		if(loglog!=null){
			outstream.println("Unique "+loglog.k+"-mers:         \t"+loglog.cardinality());
		}
	}
	
	/**
	 * Clear stored kmers.
	 */
	public void unloadKmers(){
		if(keySets!=null){
			for(int i=0; i<keySets.length; i++){keySets[i]=null;}
		}
	}
	
	/**
	 * Clear stored sequence data.
	 */
	public void unloadScaffolds(){
		if(scaffoldNames!=null && !scaffoldNames.isEmpty()){
			scaffoldNames.clear();
			scaffoldNames.trimToSize();
		}
		scaffoldReadCounts=null;
		scaffoldBaseCounts=null;
		hitCounts=null;
		scaffoldLengths=null;
	}
	
	/**
	 * Write statistics about how many reads matched each reference scaffold.
	 */
	private void writeStats(){
		if(outstats==null){return;}
		final TextStreamWriter tsw=new TextStreamWriter(outstats, overwrite, false, false);
		tsw.start();
		
		long rsum=0, bsum=0;
		
		/* Create StringNum list of scaffold names and hitcounts */
		ArrayList<StringNum> list=new ArrayList<StringNum>();
		for(int i=1; i<scaffoldNames.size(); i++){
			final long num1=scaffoldReadCounts.get(i), num2=scaffoldBaseCounts.get(i);
			if(num1>0 || !printNonZeroOnly){
				rsum+=num1;
				bsum+=num2;
				final String s=scaffoldNames.get(i);
				final int len=scaffoldLengths.get(i);
				final StringNum sn=new StringNum(s, len, num1, num2);
				list.add(sn);
			}
		}
		Collections.sort(list);
		final double rmult=100.0/(readsIn>0 ? readsIn : 1);
		final double bmult=100.0/(basesIn>0 ? basesIn : 1);
		
		tsw.print("#File\t"+in1+(in2==null ? "" : "\t"+in2)+"\n");
		
		if(STATS_COLUMNS==3){
			tsw.print(String.format("#Total\t%d\n",readsIn));
			tsw.print(String.format("#Matched\t%d\t%.5f%%\n",rsum,rmult*rsum));
			tsw.print("#Name\tReads\tReadsPct\n");
			for(int i=0; i<list.size(); i++){
				StringNum sn=list.get(i);
				tsw.print(String.format("%s\t%d\t%.5f%%\n",sn.name,sn.reads,(sn.reads*rmult)));
			}
		}else{
			tsw.print(String.format("#Total\t%d\t%d\n",readsIn,basesIn));
			tsw.print(String.format("#Matched\t%d\t%.5f%%\n",rsum,rmult*rsum,bsum,bsum*bmult));
			tsw.print("#Name\tReads\tReadsPct\tBases\tBasesPct\n");
			for(int i=0; i<list.size(); i++){
				StringNum sn=list.get(i);
				tsw.print(String.format("%s\t%d\t%.5f%%\t%d\t%.5f%%\n",sn.name,sn.reads,(sn.reads*rmult),sn.bases,(sn.bases*bmult)));
			}
		}
		tsw.poisonAndWait();
	}
	
	/**
	 * Write RPKM statistics.
	 */
	private void writeRPKM(){
		if(outrpkm==null){return;}
		final TextStreamWriter tsw=new TextStreamWriter(outrpkm, overwrite, false, false);
		tsw.start();

		/* Count mapped reads */
		long mapped=0;
		for(int i=0; i<scaffoldReadCounts.length(); i++){
			mapped+=scaffoldReadCounts.get(i);
		}
		
		/* Print header */
		tsw.print("#File\t"+in1+(in2==null ? "" : "\t"+in2)+"\n");
		tsw.print(String.format("#Reads\t%d\n",readsIn));
		tsw.print(String.format("#Mapped\t%d\n",mapped));
		tsw.print(String.format("#RefSequences\t%d\n",Tools.max(0, scaffoldNames.size()-1)));
		tsw.print("#Name\tLength\tBases\tCoverage\tReads\tRPKM\n");
		
		final float mult=1000000000f/Tools.max(1, mapped);
		
		/* Print data */
		for(int i=1; i<scaffoldNames.size(); i++){
			final long reads=scaffoldReadCounts.get(i);
			final long bases=scaffoldBaseCounts.get(i);
			final String s=scaffoldNames.get(i);
			final int len=scaffoldLengths.get(i);
			final double invlen=1.0/Tools.max(1, len);
			final double mult2=mult*invlen;
			if(reads>0 || !printNonZeroOnly){
				tsw.print(String.format("%s\t%d\t%d\t%.4f\t%d\t%.4f\n",s,len,bases,bases*invlen,reads,reads*mult2));
			}
		}
		tsw.poisonAndWait();
	}
	
	/**
	 * Write statistics on a per-reference basis.
	 */
	private void writeRefStats(){
		if(outrefstats==null){return;}
		final TextStreamWriter tsw=new TextStreamWriter(outrefstats, overwrite, false, false);
		tsw.start();
		
		/* Count mapped reads */
		long mapped=0;
		for(int i=0; i<scaffoldReadCounts.length(); i++){
			mapped+=scaffoldReadCounts.get(i);
		}
		
		final int numRefs=refNames.size();
		long[] refReadCounts=new long[numRefs];
		long[] refBaseCounts=new long[numRefs];
		long[] refLengths=new long[numRefs];
		
		for(int r=0, s=1; r<numRefs; r++){
			final int lim=s+refScafCounts[r];
			while(s<lim){
				refReadCounts[r]+=scaffoldReadCounts.get(s);
				refBaseCounts[r]+=scaffoldBaseCounts.get(s);
				refLengths[r]+=scaffoldLengths.get(s);
				s++;
			}
		}
		
		/* Print header */
		tsw.print("#File\t"+in1+(in2==null ? "" : "\t"+in2)+"\n");
		tsw.print(String.format("#Reads\t%d\n",readsIn));
		tsw.print(String.format("#Mapped\t%d\n",mapped));
		tsw.print(String.format("#References\t%d\n",Tools.max(0, refNames.size())));
		tsw.print("#Name\tLength\tScaffolds\tBases\tCoverage\tReads\tRPKM\n");
		
		final float mult=1000000000f/Tools.max(1, mapped);
		
		/* Print data */
		for(int i=0; i<refNames.size(); i++){
			final long reads=refReadCounts[i];
			final long bases=refBaseCounts[i];
			final long len=refLengths[i];
			final int scafs=refScafCounts[i];
			final String name=ReadWrite.stripToCore(refNames.get(i));
			final double invlen=1.0/Tools.max(1, len);
			final double mult2=mult*invlen;
			if(reads>0 || !printNonZeroOnly){
				tsw.print(String.format("%s\t%d\t%d\t%d\t%.4f\t%d\t%.4f\n",name,len,scafs,bases,bases*invlen,reads,reads*mult2));
			}
		}
		tsw.poisonAndWait();
	}
	
	/**
	 * Write processing statistics in DUK's format.
	 * @param time Elapsed time, nanoseconds
	 */
	private void writeDuk(long time){
		if(outduk==null){return;}
		final TextStreamWriter tsw=new TextStreamWriter(outduk, overwrite, false, false);
		tsw.start();
		tsw.println(dukString(time));
		tsw.poisonAndWait();
	}
	
	/**
	 * Write RQCFilter stats.
	 * @param time Elapsed time, nanoseconds
	 */
	private void writeRqc(){
		if(outrqc==null){return;}
		addToRqcMap();
		if(outrqc.endsWith("hashmap")){return;}
		final TextStreamWriter tsw=new TextStreamWriter(outrqc, overwrite, false, false);
		tsw.start();
		tsw.println(rqcString());
		tsw.poisonAndWait();
	}
	
	public static String rqcString(){
		if(RQC_MAP==null){return null;}
		StringBuilder sb=new StringBuilder();
		
		String[] keys=new String[] {"inputReads", "inputBases", "qtrimmedReads", "qtrimmedBases", "qfilteredReads", "qfilteredBases",
				"ktrimmedReads", "ktrimmedBases", "kfilteredReads", "kfilteredBases", "outputReads", "outputBases"};
		
		for(String key : keys){
			String value=RQC_MAP.get(key);
			if(value!=null){
				sb.append(key+"="+value+"\n");
			}
		}
		
		return sb.toString();
	}
	
	private void addToRqcMap(){
		putRqc("inputReads", readsIn, false);
		putRqc("inputBases", basesIn, false);
		if(qtrimLeft || qtrimRight){
			putRqc("qtrimmedReads", readsQTrimmed, false);
			putRqc("qtrimmedBases", basesQTrimmed, false);
		}
		putRqc("qfilteredReads", readsQFiltered, false);
		putRqc("qfilteredBases", basesQFiltered, false);
		
		if(ktrimLeft || ktrimRight || ktrimN){
			putRqc("ktrimmedReads", readsKTrimmed, true);
			putRqc("ktrimmedBases", basesKTrimmed, true);
		}else{
			putRqc("kfilteredReads", readsKFiltered, false);
			putRqc("kfilteredBases", basesKFiltered, false);
		}
		putRqc("outputReads", readsOut, true);
		putRqc("outputBases", basesOut, true);
	}
	
	private static void putRqc(String key, long value, boolean evict){putRqc(key, value+"", evict);}
	
	private static void putRqc(String key, String value, boolean evict){
		if(RQC_MAP==null){RQC_MAP=new HashMap<String,String>();}
		if(evict || !RQC_MAP.containsKey(key)){RQC_MAP.put(key, value);}
	}
	
	/**
	 * Helper method; formats statistics to be duk-compatible
	 * @param time Elapsed time, nanoseconds
	 * @return duk output string
	 */
	private String dukString(long time){
		StringBuilder sb=new StringBuilder();
		sb.append("##INPUT PARAMETERS##\n");
		sb.append("#Reference file:	"+(ref==null || ref.length<1 ? null : ref.length==1 ? ref[0] : Arrays.toString(ref))+"\n");
		sb.append("#Query file:	"+in1+(in2==null ? "" : ","+in2)+"\n");
		sb.append("#Not matched reads file:	"+out1+(out2==null ? "" : ","+out2)+"\n");
		sb.append("#Matched reads file:	"+outb1+(outb2==null ? "" : ","+outb2)+"\n");
		sb.append("#Output file (duk):	"+outduk+"\n");
		sb.append("#Output file (stats):	"+outstats+"\n");
		sb.append("#Mer size:	"+k+"\n");
		long size=0;
		for(AbstractKmerTable x : keySets){size+=x.size();}
		sb.append("#Avg step size:	"+String.format("%.1f", refKmers/(double)(Tools.max(1, size)))+"\n");
		sb.append("#Cut off:	"+maxBadKmers0+"\n");
		sb.append("#Mask middle:	"+maskMiddle+"\n");
		sb.append("#Quality trim:	"+((qtrimLeft || qtrimRight) ? trimq : "false")+"\n");
		sb.append("\n");
		
		sb.append("##REFERENCE STAT##\n");
		sb.append("#Total Reads:	"+refReads+"\n");
		sb.append("#Total Bases:	"+refBases+"\n");
		sb.append("#Total kmers:	"+refKmers+"\n");
		sb.append("#Total stored kmers:	"+size+"\n");
		sb.append("\n");

		sb.append("## ELAPSED TIME##\n");
		sb.append("# Time:	"+String.format("%.2f", time/1000000000.0)+" seconds\n");
		sb.append("\n");

		sb.append("##QUERY FILE STAT##\n");
		sb.append("# Total number of reads:	"+readsIn+"\n");
		sb.append("# Total number of matched reads:	"+readsKFiltered+"\n");
		sb.append("# Match ratio:	"+String.format("%.6f", readsKFiltered*1.0/readsIn)+"\n");
		sb.append("\n");

		sb.append("##P-VALUE##\n");
		sb.append("#Avg number of Kmer for each read:	"+((basesIn/(Tools.max(readsIn, 1)))-k)+"\n");
//		sb.append("# P value for the given threshold 1 is 4.05231e-14\n"); //duk prints a P value; not sure what it means
		sb.append("\n");

		sb.append("## Histogram of kmer occurance for reads with at least one occurance ##\n");
		sb.append("#NumOcc\tNumReads\tPercentage\n");
		
		long sum=Tools.sum(hitCounts);
		double mult=100.0/(sum<1 ? 1 : sum);
		for(int i=0; i<hitCounts.length; i++){
			long x=hitCounts[i];
			if(x>0){
				sb.append(i).append('\t').append(x).append('\t').append(String.format("%.4f",(x*mult))).append('\n');
			}
		}
		
		return sb.toString();
	}
	
	/**
	 * Fills the scaffold names array with reference names.
	 */
	private void toRefNames(){
		final int numRefs=refNames.size();
		for(int r=0, s=1; r<numRefs; r++){
			final int scafs=refScafCounts[r];
			final int lim=s+scafs;
			final String name=ReadWrite.stripToCore(refNames.get(r));
//			System.err.println("r="+r+", s="+s+", scafs="+scafs+", lim="+lim+", name="+name);
			while(s<lim){
//				System.err.println(r+", "+s+". Setting "+scaffoldNames.get(s)+" -> "+name);
				scaffoldNames.set(s, name);
				s++;
			}
		}
	}
	
	
	/*--------------------------------------------------------------*/
	/*----------------         Inner Methods        ----------------*/
	/*--------------------------------------------------------------*/
	

	/**
	 * Fills tables with kmers from references, using multiple LoadThread.
	 * @return Number of kmers stored.
	 */
	private long spawnLoadThreads(){
		Timer t=new Timer();
		if((ref==null || ref.length<1) && (literal==null || literal.length<1)){return 0;}
		long added=0;
		
		/* Create load threads */
		LoadThread[] loaders=new LoadThread[WAYS];
		for(int i=0; i<loaders.length; i++){
			loaders[i]=new LoadThread(i);
			loaders[i].start();
		}
		
		/* For each reference file... */
		int refNum=0;
		if(ref!=null){
			for(String refname : ref){

				/* Start an input stream */
				FileFormat ff=FileFormat.testInput(refname, FileFormat.FASTA, null, false, true);
				ConcurrentReadInputStream cris=ConcurrentReadInputStream.getReadInputStream(-1L, false, ff, null, null, null, Shared.USE_MPI, true);
				cris.start(); //4567
				ListNum<Read> ln=cris.nextList();
				ArrayList<Read> reads=(ln!=null ? ln.list : null);
				
				/* Iterate through read lists from the input stream */
				while(reads!=null && reads.size()>0){
					{
						/* Assign a unique ID number to each scaffold */
						ArrayList<Read> reads2=new ArrayList<Read>(reads);
						for(Read r1 : reads2){
							final Read r2=r1.mate;
							final Integer id=scaffoldNames.size();
							refScafCounts[refNum]++;
							scaffoldNames.add(r1.id==null ? id.toString() : r1.id);
							int len=r1.length();
							r1.obj=id;
							if(r2!=null){
								r2.obj=id;
								len+=r2.length();
							}
							scaffoldLengths.add(len);
						}
						
						if(REPLICATE_AMBIGUOUS){
							reads2=Tools.replicateAmbiguous(reads2, Tools.min(k, mink));
						}

						/* Send a pointer to the read list to each LoadThread */
						for(LoadThread lt : loaders){
							boolean b=true;
							while(b){
								try {
									lt.queue.put(reads2);
									b=false;
								} catch (InterruptedException e) {
									//TODO:  This will hang due to still-running threads.
									throw new RuntimeException(e);
								}
							}
						}
					}

					/* Dispose of the old list and fetch a new one */
					cris.returnList(ln.id, ln.list.isEmpty());
					ln=cris.nextList();
					reads=(ln!=null ? ln.list : null);
				}
				/* Cleanup */
				cris.returnList(ln.id, ln.list.isEmpty());
				errorState|=ReadWrite.closeStream(cris);
				refNum++;
			}
		}

		/* If there are literal sequences to use as references */
		if(literal!=null){
			ArrayList<Read> list=new ArrayList<Read>(literal.length);
			if(verbose){System.err.println("Adding literals "+Arrays.toString(literal));}

			/* Assign a unique ID number to each literal sequence */
			for(int i=0; i<literal.length; i++){
				final Integer id=scaffoldNames.size();
				final Read r=new Read(literal[i].getBytes(), null, id);
				refScafCounts[refNum]++;
				scaffoldNames.add(id.toString());
				scaffoldLengths.add(r.length());
				r.obj=id;
				list.add(r);
			}
			
			if(REPLICATE_AMBIGUOUS){
				list=Tools.replicateAmbiguous(list, Tools.min(k, mink));
			}

			/* Send a pointer to the read list to each LoadThread */
			for(LoadThread lt : loaders){
				boolean b=true;
				while(b){
					try {
						lt.queue.put(list);
						b=false;
					} catch (InterruptedException e) {
						//TODO:  This will hang due to still-running threads.
						throw new RuntimeException(e);
					}
				}
			}
		}
		
		/* Signal loaders to terminate */
		for(LoadThread lt : loaders){
			boolean b=true;
			while(b){
				try {
					lt.queue.put(POISON);
					b=false;
				} catch (InterruptedException e) {
					//TODO:  This will hang due to still-running threads.
					throw new RuntimeException(e);
				}
			}
		}
		
		/* Wait for loaders to die, and gather statistics */
		boolean success=true;
		for(LoadThread lt : loaders){
			while(lt.getState()!=Thread.State.TERMINATED){
				try {
					lt.join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			added+=lt.addedT;
			refKmers+=lt.refKmersT;
			refBases+=lt.refBasesT;
			refReads+=lt.refReadsT;
			modsum+=lt.modsumT;
			success&=lt.success;
		}
		if(!success){KillSwitch.kill("Failed loading ref kmers; aborting.");}
		
		//Correct statistics for number of threads, since each thread processes all reference data
		refKmers/=WAYS;
		refBases/=WAYS;
		refReads/=WAYS;
		
		scaffoldReadCounts=new AtomicLongArray(scaffoldNames.size());
		scaffoldBaseCounts=new AtomicLongArray(scaffoldNames.size());

		t.stop();
		if(DISPLAY_PROGRESS){
			outstream.println("Added "+added+" kmers; time: \t"+t);
			Shared.printMemory();
			outstream.println();
		}
		
		if(verbose){
			TextStreamWriter tsw=new TextStreamWriter("stdout", false, false, false, FileFormat.TEXT);
			tsw.start();
			for(AbstractKmerTable table : keySets){
				table.dumpKmersAsText(tsw, k, 1);
			}
			tsw.poisonAndWait();
		}
		
		return added;
	}

	/**
	 * Match reads against reference kmers, using multiple ProcessThread.
	 * @param t
	 */
	private void spawnProcessThreads(Timer t){
		t.start();
		
		/* Create read input stream */
		final ConcurrentReadInputStream cris;
		final boolean paired;
		{
			FileFormat ff1=FileFormat.testInput(in1, FileFormat.FASTQ, null, true, true);
			FileFormat ff2=FileFormat.testInput(in2, FileFormat.FASTQ, null, true, true);
			cris=ConcurrentReadInputStream.getReadInputStream(maxReads, ff1.samOrBam(), ff1, ff2, qfin1, qfin2);
			cris.setSampleRate(samplerate, sampleseed);
			cris.start(); //4567
			paired=cris.paired();
			if(!ff1.samOrBam()){outstream.println("Input is being processed as "+(paired ? "paired" : "unpaired"));}
		}
		
		/* Create read output streams */
		final ConcurrentReadOutputStream ros, rosb, ross;
		if(out1!=null){
			final int buff=(!ORDERED ? 12 : Tools.max(32, 2*Shared.threads()));
			FileFormat ff1=FileFormat.testOutput(out1, FileFormat.FASTQ, null, true, overwrite, append, ORDERED);
			FileFormat ff2=FileFormat.testOutput(out2, FileFormat.FASTQ, null, true, overwrite, append, ORDERED);
			ros=ConcurrentReadOutputStream.getStream(ff1, ff2, null, null, buff, null, true);
			ros.start();
		}else{ros=null;}
		if(outb1!=null){
			final int buff=(!ORDERED ? 12 : Tools.max(32, 2*Shared.threads()));
			FileFormat ff1=FileFormat.testOutput(outb1, FileFormat.FASTQ, null, true, overwrite, append, ORDERED);
			FileFormat ff2=FileFormat.testOutput(outb2, FileFormat.FASTQ, null, true, overwrite, append, ORDERED);
			rosb=ConcurrentReadOutputStream.getStream(ff1, ff2, null, null, buff, null, true);
			rosb.start();
		}else{rosb=null;}
		if(outsingle!=null){
			final int buff=(!ORDERED ? 12 : Tools.max(32, 2*Shared.threads()));
			FileFormat ff=FileFormat.testOutput(outsingle, FileFormat.FASTQ, null, true, overwrite, append, ORDERED);
			ross=ConcurrentReadOutputStream.getStream(ff, null, null, null, buff, null, true);
			ross.start();
		}else{ross=null;}
		if(ros!=null || rosb!=null || ross!=null){
			t.stop();
			outstream.println("Started output streams:\t"+t);
			t.start();
		}
		
		/* Optionally skip the first reads, since initial reads may have lower quality */
		if(skipreads>0){
			long skipped=0;

			ListNum<Read> ln=cris.nextList();
			ArrayList<Read> reads=(ln!=null ? ln.list : null);
			
			while(skipped<skipreads && reads!=null && reads.size()>0){
				skipped+=reads.size();
				
				if(rosb!=null){rosb.add(new ArrayList<Read>(1), ln.id);}
				if(ros!=null){ros.add(new ArrayList<Read>(1), ln.id);}
				if(ross!=null){ross.add(new ArrayList<Read>(1), ln.id);}
				
				cris.returnList(ln.id, ln.list.isEmpty());
				ln=cris.nextList();
				reads=(ln!=null ? ln.list : null);
			}
			cris.returnList(ln.id, ln.list.isEmpty());
			if(reads==null || reads.isEmpty()){
				ReadWrite.closeStreams(cris, ros, rosb, ross);
				System.err.println("Skipped all of the reads.");
				System.exit(0);
			}
		}
		
		/* Create ProcessThreads */
		ArrayList<ProcessThread> alpt=new ArrayList<ProcessThread>(THREADS);
		for(int i=0; i<THREADS; i++){alpt.add(new ProcessThread(cris, ros, rosb, ross, ALLOW_LOCAL_ARRAYS));}
		for(ProcessThread pt : alpt){pt.start();}
		
		/* Wait for threads to die, and gather statistics */
		for(ProcessThread pt : alpt){
			
			/* Wait for a thread to die */
			while(pt.getState()!=Thread.State.TERMINATED){
				try {
					pt.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			/* Accumulate data from per-thread counters */
			readsIn+=pt.readsInT;
			basesIn+=pt.basesInT;
			readsOut+=pt.readsOutuT;
			basesOut+=pt.basesOutuT;
			readsKFiltered+=pt.readsKFilteredT;
			basesKFiltered+=pt.basesKFilteredT;
			readsQTrimmed+=pt.readsQTrimmedT;
			basesQTrimmed+=pt.basesQTrimmedT;
			readsFTrimmed+=pt.readsFTrimmedT;
			basesFTrimmed+=pt.basesFTrimmedT;
			readsKTrimmed+=pt.readsKTrimmedT;
			basesKTrimmed+=pt.basesKTrimmedT;
			readsTrimmedByOverlap+=pt.readsTrimmedByOverlapT;
			basesTrimmedByOverlap+=pt.basesTrimmedByOverlapT;
			badGcReads+=pt.badGcReadsT;
			badGcBases+=pt.badGcBasesT;
			readsQFiltered+=pt.readsQFilteredT;
			basesQFiltered+=pt.basesQFilteredT;
			readsEFiltered+=pt.readsEFilteredT;
			basesEFiltered+=pt.basesEFilteredT;
			
			if(hitCounts!=null){
				for(int i=0; i<hitCounts.length; i++){hitCounts[i]+=pt.hitCountsT[i];}
				pt.hitCountsT=null;
			}
			if(pt.scaffoldReadCountsT!=null && scaffoldReadCounts!=null){
				for(int i=0; i<pt.scaffoldReadCountsT.length; i++){scaffoldReadCounts.addAndGet(i, pt.scaffoldReadCountsT[i]);}
				pt.scaffoldReadCountsT=null;
			}
			if(pt.scaffoldBaseCountsT!=null && scaffoldBaseCounts!=null){
				for(int i=0; i<pt.scaffoldBaseCountsT.length; i++){scaffoldBaseCounts.addAndGet(i, pt.scaffoldBaseCountsT[i]);}
				pt.scaffoldBaseCountsT=null;
			}
		}
		
		/* Shut down I/O streams; capture error status */
		errorState|=ReadWrite.closeStreams(cris, ros, rosb, ross);
		errorState|=ReadStats.writeAll();
		
		t.stop();
		if(showSpeed){
			outstream.println("Processing time:   \t\t"+t);
		}
	}
	
	/*--------------------------------------------------------------*/
	/*----------------         Inner Classes        ----------------*/
	/*--------------------------------------------------------------*/

	/**
	 * Loads kmers into a table.  Each thread handles all kmers X such that X%WAYS==tnum.
	 */
	private class LoadThread extends Thread{
		
		public LoadThread(final int tnum_){
			tnum=tnum_;
			map=keySets[tnum];
		}
		
		/**
		 * Get the next list of reads (or scaffolds) from the queue.
		 * @return List of reads
		 */
		private ArrayList<Read> fetch(){
			ArrayList<Read> list=null;
			while(list==null){
				try {
					list=queue.take();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return list;
		}
		
		@Override
		public void run(){
			ArrayList<Read> reads=fetch();
			while(reads!=POISON){
				for(Read r1 : reads){
					assert(r1.pairnum()==0);
					final Read r2=r1.mate;

					final int rblen=(r1==null ? 0 : r1.length());
					final int rblen2=r1.mateLength();
					
					addedT+=addToMap(r1, rblen>20000000 ? k : rblen>5000000 ? 11 : rblen>500000 ? 2 : 0);
					if(r2!=null){
						addedT+=addToMap(r2, rblen2>20000000 ? k : rblen2>5000000 ? 11 : rblen2>500000 ? 2 : 0);
					}
				}
				reads=fetch();
			}
			
			if(map.canRebalance() && map.size()>2L*map.arrayLength()){
				map.rebalance();
			}
			success=true;
		}

		/**
		 * Store the read's kmers in a table.
		 * @param r The current read to process
		 * @param skip Number of bases to skip between kmers
		 * @return Number of kmers stored
		 */
		private long addToMap(Read r, int skip){
			skip=Tools.max(minSkip, Tools.min(maxSkip, skip));
			final byte[] bases=r.bases;
			final int shift=2*k;
			final int shift2=shift-2;
			final long mask=~((-1L)<<shift);
			final long kmask=lengthMasks[k];
			long kmer=0;
			long rkmer=0;
			long added=0;
			int len=0;
			
			if(bases!=null){
				refReadsT++;
				refBasesT+=bases.length;
			}
			if(bases==null || bases.length<k){return 0;}
			
			final int id=(Integer)r.obj;
			
			if(skip>1){ //Process while skipping some kmers
				for(int i=0; i<bases.length; i++){
					byte b=bases[i];
					long x=Dedupe.baseToNumber[b];
					long x2=Dedupe.baseToComplementNumber[b];
					kmer=((kmer<<2)|x)&mask;
					rkmer=(rkmer>>>2)|(x2<<shift2);
					if(b=='N'){len=0;}else{len++;}
					if(verbose){System.err.println("Scanning1 i="+i+", kmer="+kmer+", rkmer="+rkmer+", bases="+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
					if(len>=k){
						refKmersT++;
						if(len%skip==0){
							final long extraBase=(i>=bases.length-1 ? -1 : AminoAcid.baseToNumber[bases[i+1]]);
							added+=addToMap(kmer, rkmer, k, extraBase, id, kmask, hammingDistance, editDistance);
							if(useShortKmers){
								if(i==k2){added+=addToMapRightShift(kmer, rkmer, id);}
								if(i==bases.length-1){added+=addToMapLeftShift(kmer, rkmer, extraBase, id);}
							}
						}
					}
				}
			}else{ //Process all kmers
				for(int i=0; i<bases.length; i++){
					byte b=bases[i];
					long x=Dedupe.baseToNumber[b];
					long x2=Dedupe.baseToComplementNumber[b];
					kmer=((kmer<<2)|x)&mask;
					rkmer=(rkmer>>>2)|(x2<<shift2);
					if(b=='N'){len=0;}else{len++;}
					if(verbose){System.err.println("Scanning2 i="+i+", kmer="+kmer+", rkmer="+rkmer+", bases="+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
					if(len>=k){
						refKmersT++;
						final long extraBase=(i>=bases.length-1 ? -1 : AminoAcid.baseToNumber[bases[i+1]]);
						final long atm=addToMap(kmer, rkmer, k, extraBase, id, kmask, hammingDistance, editDistance);
						added+=atm;
//						assert(false) : atm+", "+map.contains(toValue(kmer, rkmer, kmask));
						if(useShortKmers){
							if(i==k2){added+=addToMapRightShift(kmer, rkmer, id);}
							if(i==bases.length-1){added+=addToMapLeftShift(kmer, rkmer, extraBase, id);}
						}
					}
				}
			}
			return added;
		}
		

		/**
		 * Adds short kmers on the left end of the read.
		 * @param kmer Forward kmer
		 * @param rkmer Reverse kmer
		 * @param extraBase Base added to end in case of deletions
		 * @param id Scaffold number
		 * @return Number of kmers stored
		 */
		private long addToMapLeftShift(long kmer, long rkmer, final long extraBase, final int id){
			if(verbose){System.err.println("addToMapLeftShift");}
			long added=0;
			for(int i=k-1; i>=mink; i--){
				kmer=kmer&rightMasks[i];
				rkmer=rkmer>>>2;
				long x=addToMap(kmer, rkmer, i, extraBase, id, lengthMasks[i], hammingDistance2, editDistance2);
				added+=x;
				if(verbose){
					if((toValue(kmer, rkmer, lengthMasks[i]))%WAYS==tnum){
						System.err.println("added="+x+"; i="+i+"; tnum="+tnum+"; Added left-shift kmer "+AminoAcid.kmerToString(kmer&~lengthMasks[i], i)+"; value="+(toValue(kmer, rkmer, lengthMasks[i]))+"; kmer="+kmer+"; rkmer="+rkmer+"; kmask="+lengthMasks[i]+"; rightMasks[i+1]="+rightMasks[i+1]);
						System.err.println("i="+i+"; tnum="+tnum+"; Looking for left-shift kmer "+AminoAcid.kmerToString(kmer&~lengthMasks[i], i));
						final long value=toValue(kmer, rkmer, lengthMasks[i]);
						if(map.contains(value)){System.err.println("Found "+value);}
					}
				}
			}
			return added;
		}
		

		/**
		 * Adds short kmers on the right end of the read.
		 * @param kmer Forward kmer
		 * @param rkmer Reverse kmer
		 * @param id Scaffold number
		 * @return Number of kmers stored
		 */
		private long addToMapRightShift(long kmer, long rkmer, final int id){
			if(verbose){System.err.println("addToMapRightShift");}
			long added=0;
			for(int i=k-1; i>=mink; i--){
				long extraBase=kmer&3L;
				kmer=kmer>>>2;
				rkmer=rkmer&rightMasks[i];
//				assert(Long.numberOfLeadingZeros(kmer)>=2*(32-i)) : Long.numberOfLeadingZeros(kmer)+", "+i+", "+kmer+", "+kMasks[i];
//				assert(Long.numberOfLeadingZeros(rkmer)>=2*(32-i)) : Long.numberOfLeadingZeros(rkmer)+", "+i+", "+rkmer+", "+kMasks[i];
				long x=addToMap(kmer, rkmer, i, extraBase, id, lengthMasks[i], hammingDistance2, editDistance2);
				added+=x;
				if(verbose){
					if((toValue(kmer, rkmer, lengthMasks[i]))%WAYS==tnum){
						System.err.println("added="+x+"; i="+i+"; tnum="+tnum+"; Added right-shift kmer "+AminoAcid.kmerToString(kmer&~lengthMasks[i], i)+"; value="+(toValue(kmer, rkmer, lengthMasks[i]))+"; kmer="+kmer+"; rkmer="+rkmer+"; kmask="+lengthMasks[i]+"; rightMasks[i+1]="+rightMasks[i+1]);
						System.err.println("i="+i+"; tnum="+tnum+"; Looking for right-shift kmer "+AminoAcid.kmerToString(kmer&~lengthMasks[i], i));
						final long value=toValue(kmer, rkmer, lengthMasks[i]);
						if(map.contains(value)){System.err.println("Found "+value);}
					}
				}
			}
			return added;
		}
		
		
		/**
		 * Adds this kmer to the table, including any mutations implied by editDistance or hammingDistance.
		 * @param kmer Forward kmer
		 * @param rkmer Reverse kmer
		 * @param len Kmer length
		 * @param extraBase Base added to end in case of deletions
		 * @param id Scaffold number
		 * @param kmask0
		 * @return Number of kmers stored
		 */
		private long addToMap(final long kmer, final long rkmer, final int len, final long extraBase, final int id, final long kmask0, final int hdist, final int edist){
			
			assert(kmask0==lengthMasks[len]) : kmask0+", "+len+", "+lengthMasks[len]+", "+Long.numberOfTrailingZeros(kmask0)+", "+Long.numberOfTrailingZeros(lengthMasks[len]);
			
			if(verbose){System.err.println("addToMap_A; len="+len+"; kMasks[len]="+lengthMasks[len]);}
			assert((kmer&kmask0)==0);
			final long added;
			if(hdist==0){
				final long key=toValue(kmer, rkmer, kmask0);
				if(speed>0 && ((key/WAYS)&15)<speed){return 0;}
				if(key%WAYS!=tnum){return 0;}
				if(verbose){System.err.println("addToMap_B: "+AminoAcid.kmerToString(kmer&~lengthMasks[len], len)+" = "+key);}
				added=map.setIfNotPresent(key, id);
			}else if(edist>0){
//				long extraBase=(i>=bases.length-1 ? -1 : AminoAcid.baseToNumber[bases[i+1]]);
				added=mutate(kmer, rkmer, len, id, edist, extraBase);
			}else{
				added=mutate(kmer, rkmer, len, id, hdist, -1);
			}
			if(verbose){System.err.println("addToMap added "+added+" keys.");}
			return added;
		}
		
		/**
		 * Mutate and store this kmer through 'dist' recursions.
		 * @param kmer Forward kmer
		 * @param rkmer Reverse kmer
		 * @param id Scaffold number
		 * @param dist Number of mutations
		 * @param extraBase Base added to end in case of deletions
		 * @return Number of kmers stored
		 */
		private long mutate(final long kmer, final long rkmer, final int len, final int id, final int dist, final long extraBase){
			long added=0;
			
			final long key=toValue(kmer, rkmer, lengthMasks[len]);
			
			if(verbose){System.err.println("mutate_A; len="+len+"; kmer="+kmer+"; rkmer="+rkmer+"; kMasks[len]="+lengthMasks[len]);}
			if(key%WAYS==tnum){
				if(verbose){System.err.println("mutate_B: "+AminoAcid.kmerToString(kmer&~lengthMasks[len], len)+" = "+key);}
				int x=map.setIfNotPresent(key, id);
				if(verbose){System.err.println("mutate_B added "+x+" keys.");}
				added+=x;
				assert(map.contains(key));
			}
			
			if(dist>0){
				final int dist2=dist-1;
				
				//Sub
				for(int j=0; j<4; j++){
					for(int i=0; i<len; i++){
						final long temp=(kmer&clearMasks[i])|setMasks[j][i];
						if(temp!=kmer){
							long rtemp=AminoAcid.reverseComplementBinaryFast(temp, len);
							added+=mutate(temp, rtemp, len, id, dist2, extraBase);
						}
					}
				}
				
				if(editDistance>0){
					//Del
					if(extraBase>=0 && extraBase<=3){
						for(int i=1; i<len; i++){
							final long temp=(kmer&leftMasks[i])|((kmer<<2)&rightMasks[i])|extraBase;
							if(temp!=kmer){
								long rtemp=AminoAcid.reverseComplementBinaryFast(temp, len);
								added+=mutate(temp, rtemp, len, id, dist2, -1);
							}
						}
					}

					//Ins
					final long eb2=kmer&3;
					for(int i=1; i<len; i++){
						final long temp0=(kmer&leftMasks[i])|((kmer&rightMasks[i])>>2);
						for(int j=0; j<4; j++){
							final long temp=temp0|setMasks[j][i-1];
							if(temp!=kmer){
								long rtemp=AminoAcid.reverseComplementBinaryFast(temp, len);
								added+=mutate(temp, rtemp, len, id, dist2, eb2);
							}
						}
					}
				}

			}
			
			return added;
		}
		
		/*--------------------------------------------------------------*/
		
		/** Number of kmers stored by this thread */
		public long addedT=0;
		/** Number of items encountered by this thread */
		public long refKmersT=0, refReadsT=0, refBasesT=0;
		/** Thread number; used to determine which kmers to store */
		public final int tnum;
		/** Buffer of input read lists */
		public final ArrayBlockingQueue<ArrayList<Read>> queue=new ArrayBlockingQueue<ArrayList<Read>>(32);
		/** Used to trick compiler */
		public long modsumT=0; //123
		
		/** Destination for storing kmers */
		private final AbstractKmerTable map;
		
		/** Completed successfully */
		boolean success=false;
		
	}
	
	/*--------------------------------------------------------------*/

	/**
	 * Matches read kmers against reference kmers, performs binning and/or trimming, and writes output. 
	 */
	private class ProcessThread extends Thread{
		
		/**
		 * Constructor
		 * @param cris_ Read input stream
		 * @param ros_ Unmatched read output stream (optional)
		 * @param rosb_ Matched read output stream (optional)
		 * @param ross_ Singleton read output stream (optional)
		 */
		public ProcessThread(ConcurrentReadInputStream cris_, ConcurrentReadOutputStream ros_, ConcurrentReadOutputStream rosb_, ConcurrentReadOutputStream ross_, boolean localArrays){
			cris=cris_;
			ros=ros_;
			rosb=rosb_;
			ross=ross_;
			
			readstats=(MAKE_QUALITY_HISTOGRAM || MAKE_MATCH_HISTOGRAM || MAKE_BASE_HISTOGRAM || MAKE_QUALITY_ACCURACY ||
					MAKE_EHIST || MAKE_INDELHIST || MAKE_LHIST || MAKE_GCHIST || MAKE_IDHIST) ? 
					new ReadStats() : null;
			
			final int alen=(scaffoldNames==null ? 0 : scaffoldNames.size());
			
			if(findBestMatch){
				countArray=new int[alen];
				idList=new IntList();
				countList=new IntList();
			}else{
				countArray=null;
				idList=countList=null;
			}
			
			overlapVector=(trimByOverlap ? new int[5] : null);
			
			hitCountsT=(hitCounts==null ? null : new long[hitCounts.length]);
			
			if(localArrays && alen>0 && alen<10000 && scaffoldReadCounts!=null && scaffoldBaseCounts!=null){
				scaffoldReadCountsT=new long[alen];
				scaffoldBaseCountsT=new long[alen];
			}else{
				scaffoldReadCountsT=scaffoldBaseCountsT=null;
			}
			
			if(calcEntropy){
				entropyCounts=new short[entropyKmerspace];
				entropyCountCounts=new short[entropyWindow+2];
				entropyCountCounts[0]=(short)entropyWindow;
			}else{
				entropyCounts=entropyCountCounts=null;
			}
			
			maxBasesOutmT=(maxBasesOutm>0 ? Tools.max(1, maxBasesOutm/THREADS) : -1);
			maxBasesOutuT=(maxBasesOutu>0 ? Tools.max(1, maxBasesOutu/THREADS) : -1);
		}
		
		@Override
		public void run(){
			ListNum<Read> ln=cris.nextList();
			ArrayList<Read> reads=(ln!=null ? ln.list : null);
			ArrayList<Read> bad=(rosb==null ? null : new ArrayList<Read>(Shared.READ_BUFFER_LENGTH));
			ArrayList<Read> single=new ArrayList<Read>(Shared.READ_BUFFER_LENGTH);
			
			//While there are more reads lists...
			while(reads!=null && reads.size()>0){
				
				int removed=0;
				
				//For each read (or pair) in the list...
				for(int i=0; i<reads.size(); i++){
					final Read r1=reads.get(i);
					final Read r2=r1.mate;
					
					if(!r1.validated()){r1.validate(true);}
					if(r2!=null && !r2.validated()){r2.validate(true);}
					
					if(readstats!=null){
						if(MAKE_QUALITY_HISTOGRAM){readstats.addToQualityHistogram(r1);}
						if(MAKE_BASE_HISTOGRAM){readstats.addToBaseHistogram(r1);}
						if(MAKE_MATCH_HISTOGRAM){readstats.addToMatchHistogram(r1);}
						if(MAKE_QUALITY_ACCURACY){readstats.addToQualityAccuracy(r1);}

						if(MAKE_EHIST){readstats.addToErrorHistogram(r1);}
						if(MAKE_INDELHIST){readstats.addToIndelHistogram(r1);}
						if(MAKE_LHIST){readstats.addToLengthHistogram(r1);}
						if(MAKE_GCHIST){readstats.addToGCHistogram(r1);}
						if(MAKE_IDHIST){readstats.addToIdentityHistogram(r1);}
					}

					if(loglog!=null){loglog.hash(r1);}
					
					final int initialLength1=r1.length();
					final int initialLength2=r1.mateLength();

					final int minlen1=(int)Tools.max(initialLength1*minLenFraction, minReadLength);
					final int minlen2=(int)Tools.max(initialLength2*minLenFraction, minReadLength);
					
					if(verbose){System.err.println("Considering read "+r1.id+" "+new String(r1.bases));}
					
					readsInT++;
					basesInT+=initialLength1;
					if(r2!=null){
						readsInT++;
						basesInT+=initialLength2;
					}
					
					if(chastityFilter){
						if(r1!=null && r1.failsChastity()){
							r1.setDiscarded(true);
							if(r2!=null){r2.setDiscarded(true);}
						}
					}
					
					if(removeBadBarcodes){
						if(r1!=null && !r1.discarded() && r1.failsBarcode(barcodes, failIfNoBarcode)){
							if(failBadBarcodes){KillSwitch.kill("Invalid barcode detected: "+r1.id+"\nThis can be disabled with the flag barcodefilter=f");}
							r1.setDiscarded(true);
							if(r2!=null){r2.setDiscarded(true);}
						}
					}
					
					if(recalibrateQuality){
						if(r1!=null && !r1.discarded()){
							CalcTrueQuality.recalibrate(r1);
						}
						if(r2!=null && !r2.discarded()){
							CalcTrueQuality.recalibrate(r2);
						}
					}
					
					if(filterGC && (initialLength1>0 || initialLength2>0)){
						float gc1=(initialLength1>0 ? r1.gc() : -1);
						float gc2=(initialLength2>0 ? r2.gc() : gc1);
						if(gc1==-1){gc1=gc2;}
						if(usePairGC){
							final float gc;
							if(r2==null){
								gc=gc1;
							}else{
								gc=(gc1*initialLength1+gc2*initialLength2)/(initialLength1+initialLength2);
							}
							gc1=gc2=gc;
						}
						if(r1!=null && !r1.discarded() && (gc1<minGC || gc1>maxGC)){
							r1.setDiscarded(true);
							badGcBasesT+=initialLength1;
							badGcReadsT++;
						}
						if(r2!=null && !r2.discarded() && (gc2<minGC || gc2>maxGC)){
							r2.setDiscarded(true);
							badGcBasesT+=initialLength2;
							badGcReadsT++;
						}
					}
					
					if(forceTrimLeft>0 || forceTrimRight>0 || forceTrimRight2>0 || forceTrimModulo>0){  
						if(r1!=null && !r1.discarded()){
							final int len=r1.length();
							final int a=forceTrimLeft>0 ? forceTrimLeft : 0;
							final int b0=forceTrimModulo>0 ? len-1-len%forceTrimModulo : len;
							final int b1=forceTrimRight>0 ? forceTrimRight : len;
							final int b2=forceTrimRight2>0 ? len-1-forceTrimRight2 : len;
							final int b=Tools.min(b0, b1, b2);
							final int x=TrimRead.trimToPosition(r1, a, b, 1);
							basesFTrimmedT+=x;
							readsFTrimmedT+=(x>0 ? 1 : 0);
							if(r1.length()<minlen1){r1.setDiscarded(true);}
						}
						if(r2!=null && !r2.discarded()){
							final int len=r2.length();
							final int a=forceTrimLeft>0 ? forceTrimLeft : 0;
							final int b0=forceTrimModulo>0 ? len-1-len%forceTrimModulo : len;
							final int b1=forceTrimRight>0 ? forceTrimRight : len;
							final int b2=forceTrimRight2>0 ? len-1-forceTrimRight2 : len;
							final int b=Tools.min(b0, b1, b2);
							final int x=TrimRead.trimToPosition(r2, a, b, 1);
							basesFTrimmedT+=x;
							readsFTrimmedT+=(x>0 ? 1 : 0);
							if(r2.length()<minlen2){r2.setDiscarded(true);}
						}
					}
					
					boolean remove;
					if(removePairsIfEitherBad){remove=r1.discarded() || (r2!=null && r2.discarded());}
					else{remove=r1.discarded() && (r2==null || r2.discarded());}
					
					if(remove){
						if(r1!=null){
							basesQFilteredT+=r1.length();
							readsQFilteredT++;
						}
						if(r2!=null){
							basesQFilteredT+=r2.length();
							readsQFilteredT++;
						}
						if(bad!=null){bad.add(r1);}
					}else{
						
						if(ecc && r1!=null && r2!=null){BBMerge.findOverlapStrict(r1, r2, true);}
						
						//Process kmers
						if(ktrimLeft || ktrimRight || ktrimN){
							
							int rlen1=0, rlen2=0;
							int xsum=0;
							int rktsum=0;
							
							if(ktrimN){
								if(r1!=null){
									int x=kmask(r1, keySets);
									xsum+=x;
									rktsum+=(x>0 ? 1 : 0);
									rlen1=r1.length();
									if(rlen1<minlen1){r1.setDiscarded(true);}
								}
								if(r2!=null){
									int x=kmask(r2, keySets);
									xsum+=x;
									rktsum+=(x>0 ? 1 : 0);
									rlen2=r2.length();
									if(rlen2<minlen2){r2.setDiscarded(true);}
								}
							}else{
								if(r1!=null){
									int x=ktrim(r1, keySets);
									xsum+=x;
									rktsum+=(x>0 ? 1 : 0);
									rlen1=r1.length();
									if(rlen1<minlen1){r1.setDiscarded(true);}
								}
								if(r2!=null){
									int x=ktrim(r2, keySets);
									xsum+=x;
									rktsum+=(x>0 ? 1 : 0);
									rlen2=r2.length();
									if(rlen2<minlen2){r2.setDiscarded(true);}
								}
							}
							
							if((removePairsIfEitherBad && (r1.discarded() || (r2!=null && r2.discarded()))) || 
									(r1.discarded() && (r2==null || r2.discarded()))){
								if(!ktrimN){
									xsum+=(rlen1+rlen2);
									rktsum=(r1==null ? 0 : 1)+(r2==null ? 0 : 1);
								}
								remove=true;
								if(addTrimmedToBad && bad!=null){bad.add(r1);}
							}else if(ktrimRight && trimPairsEvenly && xsum>0 && r2!=null && r1.length()!=r2.length()){
								int x;
								if(r1.length()>r2.length()){
									x=TrimRead.trimToPosition(r1, 0, r2.length()-1, 1);
								}else{
									x=TrimRead.trimToPosition(r2, 0, r1.length()-1, 1);
								}
								if(rktsum<2){rktsum++;}
								xsum+=x;
								assert(r1.length()==r2.length()) : r1.length()+", "+r2.length();
							}
							basesKTrimmedT+=xsum;
							readsKTrimmedT+=rktsum;
							
						}else{
							//Do kmer matching
							
							if(minCoveredFraction>0){
								if(r1!=null && !r1.discarded()){
									final int minCoveredBases=(int)Math.ceil(minCoveredFraction*r1.length());
									final int covered=countCoveredBases(r1, keySets, minCoveredBases);
									if(covered>=minCoveredBases){r1.setDiscarded(true);}
								}
								if(r2!=null && !r2.discarded()){
									final int minCoveredBases=(int)Math.ceil(minCoveredFraction*r2.length());
									final int covered=countCoveredBases(r2, keySets, minCoveredBases);
									if(covered>=minCoveredBases){r2.setDiscarded(true);}
								}
							}else{

								final int maxBadKmersR1, maxBadKmersR2;
								if(minKmerFraction==0){
									maxBadKmersR1=maxBadKmersR2=maxBadKmers0;
								}else{
									final int vk1=r1.numValidKmers(keff), vk2=(r2==null ? 0 : r2.numValidKmers(keff));
									maxBadKmersR1=Tools.max(maxBadKmers0, (int)((vk1-1)*minKmerFraction));
									maxBadKmersR2=Tools.max(maxBadKmers0, (int)((vk2-1)*minKmerFraction));
								}
								
								if(!findBestMatch){
									final int a=(kbig<=k ? countSetKmers(r1, keySets, maxBadKmersR1) : countSetKmersBig(r1, keySets, maxBadKmersR1));
									final int b=(kbig<=k ? countSetKmers(r2, keySets, maxBadKmersR2) : countSetKmersBig(r2, keySets, maxBadKmersR2));

									if(r1!=null && a>maxBadKmersR1){r1.setDiscarded(true);}
									if(r2!=null && b>maxBadKmersR2){r2.setDiscarded(true);}

								}else{
									final int a=findBestMatch(r1, keySets, maxBadKmersR1);
									final int b=findBestMatch(r2, keySets, maxBadKmersR2);

									if(r1!=null && a>0){r1.setDiscarded(true);}
									if(r2!=null && b>0){r2.setDiscarded(true);}
								}
							}
							
							if((removePairsIfEitherBad && (r1.discarded() || (r2!=null && r2.discarded()))) || 
									(r1.discarded() && (r2==null || r2.discarded()))){
								remove=true;
								if(r1!=null){
									readsKFilteredT++;
									basesKFilteredT+=r1.length();
								}
								if(r2!=null){
									readsKFilteredT++;
									basesKFilteredT+=r2.length();
								}
								if(bad!=null){bad.add(r1);}
							}
							
						}
					}
					
//					assert(false) : remove+", "+trimByOverlap+", "+(r2!=null);
					
					if(!remove && trimByOverlap && r2!=null && expectedErrors(r1, r2)<meeFilter){
						
						if(aprob==null || aprob.length<r1.length()){aprob=new float[r1.length()];}
						if(bprob==null || bprob.length<r2.length()){bprob=new float[r2.length()];}
						
						//Do overlap trimming
						r2.reverseComplement();
//						int bestInsert=BBMergeOverlapper.mateByOverlap(r1, r2, aprob, bprob, overlapVector, minOverlap0, minOverlap,
//								overlapMargin, overlapMaxMismatches0, overlapMaxMismatches, overlapMinq);
						int bestInsert=BBMergeOverlapper.mateByOverlapRatio(r1, r2, aprob, bprob, overlapVector, minOverlap0, minOverlap,
								minInsert0, minInsert, maxRatio, ratioMargin, ratioOffset, 0.95f, 0.95f, useQualityForOverlap);
						
						if(bestInsert<minInsert){bestInsert=-1;}
						boolean ambig=(overlapVector[4]==1);
						final int bestBad=overlapVector[2];
						
						if(bestInsert>0 && !ambig && r1.quality!=null && r2.quality!=null && useQualityForOverlap){
							if(efilterRatio>0 && bestInsert>0 && !ambig){
								float bestExpected=BBMergeOverlapper.expectedMismatches(r1, r2, bestInsert);
								if((bestExpected+efilterOffset)*efilterRatio<bestBad){ambig=true;}
							}
							if(pfilterRatio>0 && bestInsert>0 && !ambig){
								float probability=BBMergeOverlapper.probability(r1, r2, bestInsert);
								if(probability<pfilterRatio){bestInsert=-1;}
							}
							if(meeFilter>=0 && bestInsert>0 && !ambig){
								float expected=BBMergeOverlapper.expectedMismatches(r1, r2, bestInsert);
								if(expected>meeFilter){bestInsert=-1;}
							}
						}
						
						r2.reverseComplement();
						
						if(bestInsert>0 && !ambig){
							if(bestInsert<r1.length()){
								if(verbose){System.err.println("Overlap right trimming r1 to "+0+", "+(bestInsert-1));}
								int x=TrimRead.trimToPosition(r1, 0, bestInsert-1, 1);
								if(verbose){System.err.println("Trimmed "+x+" bases: "+new String(r1.bases));}
								readsTrimmedByOverlapT++;
								basesTrimmedByOverlapT+=x;
							}
							if(bestInsert<r2.length()){
								if(verbose){System.err.println("Overlap right trimming r2 to "+0+", "+(bestInsert-1));}
								int x=TrimRead.trimToPosition(r2, 0, bestInsert-1, 1);
								if(verbose){System.err.println("Trimmed "+x+" bases: "+new String(r2.bases));}
								readsTrimmedByOverlapT++;
								basesTrimmedByOverlapT+=x;
							}
						}
					}
					
					if(!remove){
						//Do quality trimming
						
						int rlen1=0, rlen2=0;
						if(r1!=null){
							if(qtrimLeft || qtrimRight){
								int x=TrimRead.trimFast(r1, qtrimLeft, qtrimRight, trimq, 1);
								basesQTrimmedT+=x;
								readsQTrimmedT+=(x>0 ? 1 : 0);
							}
							rlen1=r1.length();
							if(rlen1<minlen1 || rlen1>maxReadLength){r1.setDiscarded(true);}
						}
						if(r2!=null){
							if(qtrimLeft || qtrimRight){
								int x=TrimRead.trimFast(r2, qtrimLeft, qtrimRight, trimq, 1);
								basesQTrimmedT+=x;
								readsQTrimmedT+=(x>0 ? 1 : 0);
							}
							rlen2=r2.length();
							if(rlen2<minlen2 || rlen2>maxReadLength){r2.setDiscarded(true);}
						}
						
						//Discard reads if too short
						if((removePairsIfEitherBad && (r1.discarded() || (r2!=null && r2.discarded()))) || 
								(r1.discarded() && (r2==null || r2.discarded()))){
							basesQTrimmedT+=(r1.length()+r1.mateLength());
							remove=true;
							if(addTrimmedToBad && bad!=null){bad.add(r1);}
						}
						
					}
					
					if(!remove){
						//Do quality filtering
						
						//Determine whether to discard the reads based on average quality
						if(minAvgQuality>0){
							if(r1!=null && r1.quality!=null && r1.avgQuality(false, minAvgQualityBases)<minAvgQuality){r1.setDiscarded(true);}
							if(r2!=null && r2.quality!=null && r2.avgQuality(false, minAvgQualityBases)<minAvgQuality){r2.setDiscarded(true);}
						}
						//Determine whether to discard the reads based on the presence of Ns
						if(maxNs>=0){
							if(r1!=null && r1.countUndefined()>maxNs){r1.setDiscarded(true);}
							if(r2!=null && r2.countUndefined()>maxNs){r2.setDiscarded(true);}
						}
						//Determine whether to discard the reads based on a lack of useful kmers
						if(minConsecutiveBases>0){
							if(r1!=null && !r1.discarded() && !r1.hasMinConsecutiveBases(minConsecutiveBases)){r1.setDiscarded(true);}
							if(r2!=null && !r2.discarded() && !r2.hasMinConsecutiveBases(minConsecutiveBases)){r2.setDiscarded(true);}
						}
						//Determine whether to discard the reads based on minimum base frequency
						if(minBaseFrequency>0){
							if(r1!=null && r1.minBaseCount()<minBaseFrequency*r1.length()){r1.setDiscarded(true);}
							if(r2!=null && r2.minBaseCount()<minBaseFrequency*r2.length()){r2.setDiscarded(true);}
						}
						
						//Discard reads if too short
						if((removePairsIfEitherBad && (r1.discarded() || (r2!=null && r2.discarded()))) || 
								(r1.discarded() && (r2==null || r2.discarded()))){
							basesQFilteredT+=(r1.length()+r1.mateLength());
							readsQFilteredT+=1+r1.mateCount();
							remove=true;
							if(addTrimmedToBad && bad!=null){bad.add(r1);}
						}
					}
					
					if(!remove && calcEntropy){
						//Test entropy
						
						if(r1!=null && !r1.discarded() && entropyCutoff>averageEntropy(r1.bases, entropyK, entropyWindow, 
								entropyCounts, entropyCountCounts, entropyKmerspace, verifyEntropy)){r1.setDiscarded(true);}
						if(r2!=null && !r2.discarded() && entropyCutoff>averageEntropy(r2.bases, entropyK, entropyWindow, 
								entropyCounts, entropyCountCounts, entropyKmerspace, verifyEntropy)){r2.setDiscarded(true);}
						
						if((removePairsIfEitherBad && (r1.discarded() || (r2!=null && r2.discarded()))) || 
								(r1.discarded() && (r2==null || r2.discarded()))){
							basesEFilteredT+=(r1.length()+r1.mateLength());
							readsEFilteredT+=(r1==null ? 0 : 1)+(r2==null ? 0 : 1);
							remove=true;
							if(bad!=null){bad.add(r1);}
						}
					}
					
					if(ross!=null){
						if(!r1.discarded() && (r2==null || r2.discarded())){
							Read clone=r1.clone();
							clone.mate=null;
							single.add(clone);
						}else if(r2!=null && r1.discarded() && !r2.discarded()){
							Read clone=r2.clone();
							clone.mate=null;
							single.add(clone);
						}
					}
					
					if(remove){
						//Evict read
						removed++;
						if(r2!=null){removed++;}
						reads.set(i, null);
						
						readsOutmT+=1+r1.mateCount();
						basesOutmT+=r1.length()+r1.mateLength();
					}else{
						readsOutuT+=1+r1.mateCount();
						basesOutuT+=r1.length()+r1.mateLength();
					}
				}
				
				//Send matched list to matched output stream
				if(rosb!=null){
					rosb.add(bad, ln.id);
					bad.clear();
				}
				
				//Send unmatched list to unmatched output stream
				if(ros!=null){
					ros.add((removed>0 ? Tools.condenseNew(reads) : reads), ln.id); //Creates a new list if old one became empty, to prevent shutting down the cris.
				}
				
				if(ross!=null){
					ross.add(single, ln.id);
					single.clear();
				}

				if(maxBasesOutmT>=0 && basesOutmT>=maxBasesOutmT){break;}
				if(maxBasesOutuT>=0 && basesOutuT>=maxBasesOutuT){break;}
				
				//Fetch a new read list
				cris.returnList(ln.id, ln.list.isEmpty());
				ln=cris.nextList();
				reads=(ln!=null ? ln.list : null);
			}
			cris.returnList(ln.id, ln.list.isEmpty());
		}
		
		/*--------------------------------------------------------------*/
		/*----------------        Helper Methods        ----------------*/
		/*--------------------------------------------------------------*/
		
		/**
		 * Transforms a kmer into all canonical values for a given Hamming distance.
		 * Returns the related id stored in the tables.
		 * @param kmer Forward kmer
		 * @param rkmer Reverse kmer
		 * @param lengthMask Bitmask with single '1' set to left of kmer
		 * @param qPos Position of kmer in query
		 * @param len kmer length
		 * @param qHDist Hamming distance
		 * @param sets Kmer hash tables
		 * @return Value stored in table, or -1
		 */
		private final int getValue(final long kmer, final long rkmer, final long lengthMask, final int qPos, final int len, final int qHDist, final AbstractKmerTable[] sets){
			assert(lengthMask==0 || (kmer<lengthMask && rkmer<lengthMask)) : lengthMask+", "+kmer+", "+rkmer;
			int id=getValue(kmer, rkmer, lengthMask, qPos, sets);
			if(id<1 && qHDist>0){
				final int qHDist2=qHDist-1;
				
				//Sub
				for(int j=0; j<4 && id<1; j++){
					for(int i=0; i<len && id<1; i++){
						final long temp=(kmer&clearMasks[i])|setMasks[j][i];
//						System.err.println(i+", "+j+", "+setMasks[j][i]+", "+qHDist);
						if(temp!=kmer){
							long rtemp=AminoAcid.reverseComplementBinaryFast(temp, len);
//							assert(lengthMask==0 || (temp<lengthMask && rtemp<lengthMask)) : lengthMask+", "+temp+", "+rtemp+", "+kmer+", "+rkmer+
//							"\n"+len+", "+Long.numberOfTrailingZeros(lengthMask)+"\n"+
//									Long.toBinaryString(lengthMask|0x8000000000000000L)+"\n"+
//											Long.toBinaryString(temp|0x8000000000000000L)+"\n"+
//													Long.toBinaryString(rtemp|0x8000000000000000L);
							id=getValue(temp, rtemp, lengthMask, qPos, len, qHDist2, sets);
						}
					}
				}
			}
			return id;
		}
		
		/**
		 * Transforms a kmer into a canonical value stored in the table and search.
		 * @param kmer Forward kmer
		 * @param rkmer Reverse kmer
		 * @param lengthMask Bitmask with single '1' set to left of kmer
		 * @param qPos Position of kmer in query
		 * @param sets Kmer hash tables
		 * @return Value stored in table
		 */
		private final int getValue(final long kmer, final long rkmer, final long lengthMask, final int qPos, final AbstractKmerTable[] sets){
			assert(lengthMask==0 || (kmer<lengthMask && rkmer<lengthMask)) : lengthMask+", "+kmer+", "+rkmer;
			if(qSkip>1 && (qPos%qSkip!=0)){return -1;}
			
			final long max=(rcomp ? Tools.max(kmer, rkmer) : kmer);
			final long key=(max&middleMask)|lengthMask;
			if(noAccel || ((key/WAYS)&15)>=speed){
				if(verbose){System.err.println("Testing key "+key);}
				AbstractKmerTable set=sets[(int)(key%WAYS)];
				final int id=set.getValue(key);
				return id;
			}
			return -1;
		}
		
		
		/**
		 * Counts the number of kmer hits for a read.
		 * @param r Read to process
		 * @param sets Kmer tables
		 * @return Number of hits
		 */
		private final int countSetKmers(final Read r, final AbstractKmerTable[] sets, final int maxBadKmers){
			if(r==null || r.length()<k || storedKmers<1){return 0;}
			if((skipR1 && r.pairnum()==0) || (skipR2 && r.pairnum()==1)){return 0;}
			final byte[] bases=r.bases;
			final int minlen=k-1;
			final int minlen2=(maskMiddle ? k/2 : k);
			final int shift=2*k;
			final int shift2=shift-2;
			final long mask=~((-1L)<<shift);
			final long kmask=lengthMasks[k];
			long kmer=0;
			long rkmer=0;
			int found=0;
			int len=0;
			
			final int start=(restrictRight<1 ? 0 : Tools.max(0, bases.length-restrictRight));
			final int stop=(restrictLeft<1 ? bases.length : Tools.min(bases.length, restrictLeft));
			
			/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts */
			for(int i=start; i<stop; i++){
				byte b=bases[i];
				long x=Dedupe.baseToNumber[b];
				long x2=Dedupe.baseToComplementNumber[b];
				kmer=((kmer<<2)|x)&mask;
				rkmer=(rkmer>>>2)|(x2<<shift2);
				if(b=='N' && forbidNs){len=0;}else{len++;}
				if(verbose){System.err.println("Scanning6 i="+i+", kmer="+kmer+", rkmer="+rkmer+", bases="+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
				if(len>=minlen2 && i>=minlen){
					final int id=getValue(kmer, rkmer, kmask, i, k, qHammingDistance, sets);
					if(verbose){System.err.println("Testing kmer "+kmer+"; id="+id);}
					if(id>0){
						if(verbose){System.err.println("Found = "+(found+1)+"/"+maxBadKmers);}
						if(found==maxBadKmers){
							if(scaffoldReadCountsT!=null){
								scaffoldReadCountsT[id]++;
								scaffoldBaseCountsT[id]+=bases.length;
							}else{
								scaffoldReadCounts.addAndGet(id, 1);
								scaffoldBaseCounts.addAndGet(id, bases.length);
							}
							if(hitCounts==null){
								return (found=found+1);
							}//Early exit, but prevents generation of histogram that goes over maxBadKmers+1.
						}
						found++;
					}
				}
			}
			
			if(hitCountsT!=null){hitCountsT[Tools.min(found, HITCOUNT_LEN)]++;}
			return found;
		}
		
		
		/**
		 * Counts the number of kmer hits for a read.
		 * @param r Read to process
		 * @param sets Kmer tables
		 * @return Number of hits
		 */
		private final int countCoveredBases(final Read r, final AbstractKmerTable[] sets, final int minCoveredBases){
			if(r==null || r.length()<k || storedKmers<1){return 0;}
			if((skipR1 && r.pairnum()==0) || (skipR2 && r.pairnum()==1)){return 0;}
			final byte[] bases=r.bases;
			final int minlen=k-1;
			final int minlen2=(maskMiddle ? k/2 : k);
			final int shift=2*k;
			final int shift2=shift-2;
			final long mask=~((-1L)<<shift);
			final long kmask=lengthMasks[k];
			long kmer=0;
			long rkmer=0;
			int found=0;
			int len=0;
			int lastFound=-1;
			boolean recorded=false;
			
			final int start=(restrictRight<1 ? 0 : Tools.max(0, bases.length-restrictRight));
			final int stop=(restrictLeft<1 ? bases.length : Tools.min(bases.length, restrictLeft));
			
			/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts */
			for(int i=start; i<stop; i++){
				byte b=bases[i];
				long x=Dedupe.baseToNumber[b];
				long x2=Dedupe.baseToComplementNumber[b];
				kmer=((kmer<<2)|x)&mask;
				rkmer=(rkmer>>>2)|(x2<<shift2);
				if(b=='N' && forbidNs){len=0;}else{len++;}
				if(verbose){System.err.println("Scanning6 i="+i+", kmer="+kmer+", rkmer="+rkmer+", bases="+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
				if(len>=minlen2 && i>=minlen){
					final int id=getValue(kmer, rkmer, kmask, i, k, qHammingDistance, sets);
					if(verbose){System.err.println("Testing kmer "+kmer+"; id="+id);}
					if(id>0){
						
						int extra=Tools.min(k, i-lastFound);
						found+=extra;
						lastFound=i;
						
						if(verbose){System.err.println("Found = "+found+"/"+minCoveredBases);}
						if(found>=minCoveredBases){
							if(!recorded){
								if(scaffoldReadCountsT!=null){
									scaffoldReadCountsT[id]++;
									scaffoldBaseCountsT[id]+=bases.length;
								}else{
									scaffoldReadCounts.addAndGet(id, 1);
									scaffoldBaseCounts.addAndGet(id, bases.length);
								}
							}
							if(hitCounts==null){
								return found;
							}
						}
					}
				}
			}
			
			if(hitCountsT!=null){hitCountsT[Tools.min(found, HITCOUNT_LEN)]++;}
			return found;
		}
		
		/**
		 * Returns the id of the sequence with the most kmer matches to this read, or -1 if none are over maxBadKmers.
		 * @param r Read to process
		 * @param sets Kmer tables
		 * @return id of best match
		 */
		private final int findBestMatch(final Read r, final AbstractKmerTable[] sets, final int maxBadKmers){
			idList.size=0;
			if(r==null || r.length()<k || storedKmers<1){return -1;}
			if((skipR1 && r.pairnum()==0) || (skipR2 && r.pairnum()==1)){return -1;}
			final byte[] bases=r.bases;
			final int minlen=k-1;
			final int minlen2=(maskMiddle ? k/2 : k);
			final int shift=2*k;
			final int shift2=shift-2;
			final long mask=~((-1L)<<shift);
			final long kmask=lengthMasks[k];
			long kmer=0;
			long rkmer=0;
			int len=0;
			int found=0;
			
			final int start=(restrictRight<1 ? 0 : Tools.max(0, bases.length-restrictRight));
			final int stop=(restrictLeft<1 ? bases.length : Tools.min(bases.length, restrictLeft));
			
			/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts */
			for(int i=start; i<stop; i++){
				byte b=bases[i];
				long x=Dedupe.baseToNumber[b];
				long x2=Dedupe.baseToComplementNumber[b];
				kmer=((kmer<<2)|x)&mask;
				rkmer=(rkmer>>>2)|(x2<<shift2);
				if(b=='N' && forbidNs){len=0;}else{len++;}
				if(verbose){System.err.println("Scanning6 i="+i+", kmer="+kmer+", rkmer="+rkmer+", bases="+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
				if(len>=minlen2 && i>=minlen){
					final int id=getValue(kmer, rkmer, kmask, i, k, qHammingDistance, sets);
					if(id>0){
						countArray[id]++;
						if(countArray[id]==1){idList.add(id);}
						found++;
						if(verbose){System.err.println("Found = "+found+"/"+maxBadKmers);}
					}
				}
			}
			
			final int id, max;
			if(found>maxBadKmers){
				max=condenseLoose(countArray, idList, countList);
				int id0=-1;
				for(int i=0; i<countList.size; i++){
					if(countList.get(i)==max){
						id0=idList.get(i); break;
					}
				}
				if(rename){rename(r, idList, countList);}
				id=id0;
			}else{
				max=0;
				id=-1;
			}
			
			if(found>maxBadKmers){
				if(scaffoldReadCountsT!=null){
					scaffoldReadCountsT[id]++;
					scaffoldBaseCountsT[id]+=bases.length;
				}else{
					scaffoldReadCounts.addAndGet(id, 1);
					scaffoldBaseCounts.addAndGet(id, bases.length);
				}
			}
			
			if(hitCountsT!=null){hitCountsT[Tools.min(found, HITCOUNT_LEN)]++;}
			return id;
		}
		
		/** Estimates kmer hit counts for kmers longer than k using consecutive matches
		 * @param r
		 * @param sets
		 * @return Number of sets of consecutive hits of exactly length kbig
		 */
		private final int countSetKmersBig(final Read r, final AbstractKmerTable[] sets, final int maxBadKmers){
			if(r==null || r.length()<kbig || storedKmers<1){return 0;}
			if((skipR1 && r.pairnum()==0) || (skipR2 && r.pairnum()==1)){return 0;}
			assert(kbig>k);
			final int sub=kbig-k-1;
			assert(sub>=0) : kbig+", "+sub;
			final byte[] bases=r.bases;
			final int minlen=k-1;
			final int minlen2=(maskMiddle ? k/2 : k);
			final int shift=2*k;
			final int shift2=shift-2;
			final long mask=~((-1L)<<shift);
			final long kmask=lengthMasks[k];
			long kmer=0;
			long rkmer=0;
			int found=0;
			int len=0;

			int bkStart=-1;
			int bkStop=-1;
			int id=-1, lastId=-1;
			
			final int start=(restrictRight<1 ? 0 : Tools.max(0, bases.length-restrictRight));
			final int stop=(restrictLeft<1 ? bases.length : Tools.min(bases.length, restrictLeft));

			/* Loop through the bases, maintaining a forward and reverse kmer via bitshifts */
			for(int i=start; i<stop; i++){
				byte b=bases[i];
				long x=Dedupe.baseToNumber[b];
				long x2=Dedupe.baseToComplementNumber[b];
				kmer=((kmer<<2)|x)&mask;
				rkmer=(rkmer>>>2)|(x2<<shift2);
				if(b=='N' && forbidNs){len=0;}else{len++;}
				if(verbose){System.err.println("Scanning7 i="+i+", kmer="+kmer+", rkmer="+rkmer+", bases="+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
				if(len>=minlen2 && i>=minlen){
					id=getValue(kmer, rkmer, kmask, i, k, qHammingDistance, sets);
					if(verbose){System.err.println("Testing kmer "+kmer+"; id="+id);}
					if(id>0){
						lastId=id;
						if(bkStart==-1){bkStart=i;}
						bkStop=i;
					}else{
						if(bkStart>-1){
							int dif=bkStop-bkStart-sub;
							bkStop=bkStart=-1;
							if(dif>0){
								int old=found;
								found+=dif;
								if(found>maxBadKmers && old<=maxBadKmers){
									if(scaffoldReadCountsT!=null){
										scaffoldReadCountsT[lastId]++;
										scaffoldBaseCountsT[lastId]+=bases.length;
									}else{
										scaffoldReadCounts.addAndGet(lastId, 1);
										scaffoldBaseCounts.addAndGet(lastId, bases.length);
									}
									if(hitCounts==null){
										return found;
									}//Early exit, but prevents generation of histogram that goes over maxBadKmers+1.
								}
							}
						}
					}
				}
			}
			
			// This catches the case where valid kmers extend to the end of the read
			if(bkStart>-1){
				int dif=bkStop-bkStart-sub;
				bkStop=bkStart=-1;
				if(dif>0){
					int old=found;
					found+=dif;
					if(found>maxBadKmers && old<=maxBadKmers){
						if(scaffoldReadCountsT!=null){
							scaffoldReadCountsT[lastId]++;
							scaffoldBaseCountsT[lastId]+=bases.length;
						}else{
							scaffoldReadCounts.addAndGet(lastId, 1);
							scaffoldBaseCounts.addAndGet(lastId, bases.length);
						}
					}
				}
			}
			
			if(hitCountsT!=null){hitCountsT[Tools.min(found, HITCOUNT_LEN)]++;}
			return found;
		}
		
		/**
		 * Trim a read to remove matching kmers and everything to their left or right.
		 * @param r Read to process
		 * @param sets Kmer tables
		 * @return Number of bases trimmed
		 */
		private final int ktrim(final Read r, final AbstractKmerTable[] sets){
			assert(ktrimLeft || ktrimRight);
			if(r==null || r.length()<Tools.max(1, (useShortKmers ? Tools.min(k, mink) : k)) || storedKmers<1){return 0;}
			if((skipR1 && r.pairnum()==0) || (skipR2 && r.pairnum()==1)){return 0;}
			if(verbose){System.err.println("KTrimming read "+r.id);}
			final byte[] bases=r.bases, quals=r.quality;
			final int minlen=k-1;
			final int minlen2=(maskMiddle ? k/2 : k);
			final int shift=2*k;
			final int shift2=shift-2;
			final long mask=~((-1L)<<shift);
			final long kmask=lengthMasks[k];
			long kmer=0;
			long rkmer=0;
			int found=0;
			int len=0;
			int id0=-1; //ID of first kmer found.
			
			int minLoc=999999999, minLocExclusive=999999999;
			int maxLoc=-1, maxLocExclusive=-1;
			final int initialLength=r.length();
			
			final int start=(restrictRight<1 ? 0 : Tools.max(0, bases.length-restrictRight));
			final int stop=(restrictLeft<1 ? bases.length : Tools.min(bases.length, restrictLeft));
			
			//Scan for normal kmers
			for(int i=start; i<stop; i++){
				byte b=bases[i];
				long x=Dedupe.baseToNumber[b];
				long x2=Dedupe.baseToComplementNumber[b];
				kmer=((kmer<<2)|x)&mask;
				rkmer=(rkmer>>>2)|(x2<<shift2);
				if(b=='N' && forbidNs){len=0;}else{len++;}
				if(verbose){System.err.println("Scanning3 i="+i+", kmer="+kmer+", rkmer="+rkmer+", len="+len+", bases="+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
				if(len>=minlen2 && i>=minlen){
					final int id=getValue(kmer, rkmer, kmask, i, k, qHammingDistance, sets);
					if(id>0){
						if(id0<0){id0=id;}
						minLoc=Tools.min(minLoc, i-k+1);
						assert(minLoc>=0);
						maxLoc=i;
						found++;
					}
				}
			}
			
			if(minLoc!=minLocExclusive){minLocExclusive=minLoc+k;}
			if(maxLoc!=maxLocExclusive){maxLocExclusive=maxLoc-k;}
			
			//If nothing was found, scan for short kmers.  Only used for trimming.
			if(useShortKmers && found==0){
				assert(!maskMiddle && middleMask==-1) : maskMiddle+", "+middleMask+", k="+", mink="+mink;
				
				//Look for short kmers on left side
				if(ktrimLeft){
					kmer=0;
					rkmer=0;
					len=0;
					final int lim=Tools.min(k, stop);
					for(int i=start; i<lim; i++){
						byte b=bases[i];
						long x=Dedupe.baseToNumber[b];
						long x2=Dedupe.baseToComplementNumber[b];
						kmer=((kmer<<2)|x)&mask;
						rkmer=rkmer|(x2<<(2*len));
						len++;
						if(verbose){System.err.println("Scanning4 i="+i+", kmer="+kmer+", rkmer="+rkmer+", bases="+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
						if(len>=mink){
							
							if(verbose){
								System.err.println("Looking for left kmer  "+AminoAcid.kmerToString(kmer, len));
								System.err.println("Looking for left rkmer "+AminoAcid.kmerToString(rkmer, len));
							}
							
							final int id=getValue(kmer, rkmer, lengthMasks[len], i, len, qHammingDistance2, sets);
							if(id>0){
								if(id0<0){id0=id;}
								if(verbose){System.err.println("Found "+kmer);}
								minLoc=0;
								minLocExclusive=Tools.min(minLocExclusive, i+1);
								maxLoc=Tools.max(maxLoc, i);
								maxLocExclusive=Tools.max(maxLocExclusive, 0);
								found++;
							}
						}
					}
				}

				//Look for short kmers on right side
				if(ktrimRight){
					kmer=0;
					rkmer=0;
					len=0;
					final int lim=Tools.max(-1, stop-k);
					for(int i=stop-1; i>lim; i--){
						byte b=bases[i];
						long x=Dedupe.baseToNumber[b];
						long x2=Dedupe.baseToComplementNumber[b];
						kmer=kmer|(x<<(2*len));
						rkmer=((rkmer<<2)|x2)&mask;
						len++;
						if(verbose){System.err.println("Scanning5 i="+i+", kmer="+kmer+", rkmer="+rkmer+", bases="+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
						if(len>=mink){
							if(verbose){
								System.err.println("Looking for right kmer "+
										AminoAcid.kmerToString(kmer&~lengthMasks[len], len)+"; value="+toValue(kmer, rkmer, lengthMasks[len])+"; kmask="+lengthMasks[len]);
							}
							final int id=getValue(kmer, rkmer, lengthMasks[len], i, len, qHammingDistance2, sets);
							if(id>0){
								if(id0<0){id0=id;}
								if(verbose){System.err.println("Found "+kmer);}
								minLoc=i;
								minLocExclusive=Tools.min(minLocExclusive, bases.length);
								maxLoc=bases.length-1;
								maxLocExclusive=Tools.max(maxLocExclusive, i-1);
								found++;
							}
						}
					}
				}
			}
			
			
			if(verbose){System.err.println("found="+found+", minLoc="+minLoc+", maxLoc="+maxLoc+", minLocExclusive="+minLocExclusive+", maxLocExclusive="+maxLocExclusive);}
			
			if(found==0){return 0;}
			assert(found>0) : "Overflow in 'found' variable.";
			
			{//Increment counter for the scaffold whose kmer was first detected
				if(scaffoldReadCountsT!=null){
					scaffoldReadCountsT[id0]++;
					scaffoldBaseCountsT[id0]+=bases.length;
				}else{
					scaffoldReadCounts.addAndGet(id0, 1);
					scaffoldBaseCounts.addAndGet(id0, bases.length);
				}
			}
			
			if(trimPad!=0){
				maxLoc=Tools.mid(0, maxLoc+trimPad, bases.length);
				minLoc=Tools.mid(0, minLoc-trimPad, bases.length);
				maxLocExclusive=Tools.mid(0, maxLocExclusive+trimPad, bases.length);
				minLocExclusive=Tools.mid(0, minLocExclusive-trimPad, bases.length);
			}
			
			if(ktrimLeft){ //Trim from the read start to the rightmost kmer base
				if(verbose){System.err.println("Left trimming to "+(ktrimExclusive ? maxLocExclusive+1 : maxLoc+1)+", "+0);}
				int x=TrimRead.trimToPosition(r, ktrimExclusive ? maxLocExclusive+1 : maxLoc+1, bases.length-1, 1);
				if(verbose){System.err.println("Trimmed "+x+" bases: "+new String(r.bases));}
				return x;
			}else{ //Trim from the leftmost kmer base to the read stop 
				assert(ktrimRight);
				if(verbose){System.err.println("Right trimming to "+0+", "+(ktrimExclusive ? minLocExclusive-1 : minLoc-1));}
				int x=TrimRead.trimToPosition(r, 0, ktrimExclusive ? minLocExclusive-1 : minLoc-1, 1);
				if(verbose){System.err.println("Trimmed "+x+" bases: "+new String(r.bases));}
				return x;
			}
		}
		
		
		/**
		 * Mask a read to cover matching kmers.
		 * @param r Read to process
		 * @param sets Kmer tables
		 * @return Number of bases masked
		 */
		private final int kmask(final Read r, final AbstractKmerTable[] sets){
			assert(ktrimN);
			if(r==null || r.length()<Tools.max(1, (useShortKmers ? Tools.min(k, mink) : k)) || storedKmers<1){return 0;}
			if((skipR1 && r.pairnum()==0) || (skipR2 && r.pairnum()==1)){return 0;}
			if(verbose){System.err.println("KMasking read "+r.id);}
			final byte[] bases=r.bases, quals=r.quality;
			if(bases==null || bases.length<k){return 0;}
			final int minlen=k-1;
			final int minminlen=mink-1;
			final int minlen2=(maskMiddle ? k/2 : k);
			final int shift=2*k;
			final int shift2=shift-2;
			final long mask=~((-1L)<<shift);
			final long kmask=lengthMasks[k];
			long kmer=0;
			long rkmer=0;
			int found=0;
			int len=0;
			int id0=-1; //ID of first kmer found.
			
			final BitSet bs=new BitSet(bases.length+trimPad+1);
			if(kmaskFullyCovered){bs.set(0, bases.length);}
			
			final int minus=k-1-trimPad;
			final int plus=trimPad+1;
			
			final int start=(restrictRight<1 ? 0 : Tools.max(0, bases.length-restrictRight));
			final int stop=(restrictLeft<1 ? bases.length : Tools.min(bases.length, restrictLeft));
			
			//Scan for normal kmers
			for(int i=start; i<stop; i++){
				byte b=bases[i];
				long x=Dedupe.baseToNumber[b];
				long x2=Dedupe.baseToComplementNumber[b];
				kmer=((kmer<<2)|x)&mask;
				rkmer=(rkmer>>>2)|(x2<<shift2);
				if(b=='N' && forbidNs){len=0;}else{len++;}
				if(verbose){System.err.println("Scanning3 i="+i+", kmer="+kmer+", rkmer="+rkmer+", len="+len+", bases="+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
				
				if(i>=minlen){
					final int id;
					if(len>=minlen2){ 
						id=getValue(kmer, rkmer, kmask, i, k, qHammingDistance, sets);
					}else{
						id=-1;
					}
					if(id>0){
						if(id0<0){id0=id;}
						if(verbose){
							System.err.println("a: Found "+kmer);
							System.err.println("Setting "+Tools.max(0, i-minus)+", "+(i+plus));
							System.err.println("i="+i+", minus="+minus+", plus="+plus+", trimpad="+trimPad+", k="+k);
						}
						if(!kmaskFullyCovered){bs.set(Tools.max(0, i-minus), i+plus);}
						found++;
					}else if(kmaskFullyCovered){
						bs.clear(Tools.max(0, i-minus), i+plus);
					}
				}
			}
			
			//If nothing was found, scan for short kmers.
			if(useShortKmers){
				assert(!maskMiddle && middleMask==-1) : maskMiddle+", "+middleMask+", k="+", mink="+mink;
				
				//Look for short kmers on left side
				{
					kmer=0;
					rkmer=0;
					len=0;
					int len2=0;
					final int lim=Tools.min(k, stop);
					for(int i=start; i<lim; i++){
						byte b=bases[i];
						long x=Dedupe.baseToNumber[b];
						long x2=Dedupe.baseToComplementNumber[b];
						kmer=((kmer<<2)|x)&mask;
						rkmer=rkmer|(x2<<(2*len));
						len++;
						len2++;
						if(verbose){System.err.println("Scanning4 i="+i+", kmer="+kmer+", rkmer="+rkmer+", bases="+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
						
						if(len2>=minminlen){
							if(verbose){
								System.err.println("Looking for left kmer  "+AminoAcid.kmerToString(kmer, len));
								System.err.println("Looking for left rkmer "+AminoAcid.kmerToString(rkmer, len));
							}
							final int id;
							if(len>=mink){ 
								id=getValue(kmer, rkmer, lengthMasks[len], i, len, qHammingDistance2, sets);
							}else{
								id=-1;
							}
							if(id>0){
								if(id0<0){id0=id;}
								if(verbose){
									System.err.println("b: Found "+kmer);
									System.err.println("Setting "+0+", "+(i+plus));
								}
								if(!kmaskFullyCovered){bs.set(0, i+plus);}
								found++;
							}else if(kmaskFullyCovered){
								bs.clear(0, i+plus);
							}
						}
					}
				}

				//Look for short kmers on right side
				{
					kmer=0;
					rkmer=0;
					len=0;
					int len2=0;
					final int lim=Tools.max(-1, stop-k);
					for(int i=stop-1; i>lim; i--){
						byte b=bases[i];
						long x=Dedupe.baseToNumber[b];
						long x2=Dedupe.baseToComplementNumber[b];
						kmer=kmer|(x<<(2*len));
						rkmer=((rkmer<<2)|x2)&mask;
						len++;
						len2++;
						if(verbose){System.err.println("Scanning5 i="+i+", kmer="+kmer+", rkmer="+rkmer+", bases="+new String(bases, Tools.max(0, i-k2), Tools.min(i+1, k)));}
						
						if(len2>=minminlen){
							if(verbose){
								System.err.println("Looking for right kmer "+
										AminoAcid.kmerToString(kmer&~lengthMasks[len], len)+"; value="+toValue(kmer, rkmer, lengthMasks[len])+"; kmask="+lengthMasks[len]);
							}
							final int id;
							if(len>=mink){ 
								id=getValue(kmer, rkmer, lengthMasks[len], i, len, qHammingDistance2, sets);
							}else{
								id=-1;
							}
							if(id>0){
								if(id0<0){id0=id;}
								if(verbose){
									System.err.println("c: Found "+kmer);
									System.err.println("Setting "+Tools.max(0, i-trimPad)+", "+bases.length);
								}
								if(!kmaskFullyCovered){bs.set(Tools.max(0, i-trimPad), bases.length);}
								found++;
							}else if(kmaskFullyCovered){
								bs.clear(Tools.max(0, i-trimPad), bases.length);
							}
						}
					}
				}
			}
			
			
			if(verbose){System.err.println("found="+found+", bitset="+bs);}
			
			if(found==0){return 0;}
			assert(found>0) : "Overflow in 'found' variable.";
			
			{//Increment counter for the scaffold whose kmer was first detected
				if(scaffoldReadCountsT!=null){
					scaffoldReadCountsT[id0]++;
					scaffoldBaseCountsT[id0]+=bases.length;
				}else{
					scaffoldReadCounts.addAndGet(id0, 1);
					scaffoldBaseCounts.addAndGet(id0, bases.length);
				}
			}
//			int y=r.countNocalls();
			int cardinality=bs.cardinality();
//			assert(cardinality>0);
			
			//Replace kmer hit zone with the trim symbol
			for(int i=0; i<bases.length; i++){
				if(bs.get(i)){
					if(kmaskLowercase){
						bases[i]=(byte)Character.toLowerCase(bases[i]);
					}else{
						bases[i]=trimSymbol;
						if(quals!=null && trimSymbol=='N'){quals[i]=0;}
					}
				}
			}
//			assert(cardinality==r.countNocalls() || y>0) : cardinality+", "+r.countNocalls()+"\n"+r.length()+"\n"+bs+"\n"+r;//123
			return cardinality;
		}
		
		/**
		 * @param r
		 * @param idList
		 * @param countList
		 */
		private void rename(Read r, IntList idList, IntList countList) {
			if(r==null || idList.size<1){return;}
			StringBuilder sb=new StringBuilder();
			if(r.id==null){sb.append(r.numericID);}
			else{sb.append(r.id);}
			for(int i=0; i<idList.size; i++){
				int id=idList.get(i);
				int count=countList.get(i);
				sb.append('\t');
				sb.append(scaffoldNames.get(id));
				sb.append('=');
				sb.append(count);
			}
			r.id=sb.toString();
		}
		
		/**
		 * Pack a list of counts from an array to an IntList.
		 * @param loose Counter array
		 * @param packed Unique values
		 * @param counts Counts of values
		 * @return
		 */
		private int condenseLoose(int[] loose, IntList packed, IntList counts){
			counts.size=0;
			if(packed.size<1){return 0;}

			int max=0;
			for(int i=0; i<packed.size; i++){
				final int p=packed.get(i);
				final int c=loose[p];
				counts.add(c);
				loose[p]=0;
				max=Tools.max(max, c);
			}
			return max;
		}
		
		private float expectedErrors(Read r1, Read r2){
			float a=(r1==null ? 0 : r1.expectedErrors(false, -1));
			float b=(r2==null ? 0 : r2.expectedErrors(false, -1));
			return Tools.max(a, b);
		}
		
		/*--------------------------------------------------------------*/
		/*----------------        Entropy Methods       ----------------*/
		/*--------------------------------------------------------------*/
		
		/**
		 * Returns the average entropy over all windows for the read.
		 * @param bases Read sequence
		 * @param k Kmer length
		  * @param window Window length.
		 * @param counts An array of counts indexed by kmer
		 * @param countCounts An array of counts of counts indexed by count
		 * @param kmerspace 2^k
		 * @param verify Check consistency of data structures (slow)
		 * @return Entropy
		 */
		private float averageEntropy(final byte[] bases, final int k, 
				final int window, final short[] counts, final short[] countCounts, final int kmerspace, boolean verify){
			assert(k>0) : "k must be greater than 0";
//			Arrays.fill(counts, 0);

			assert(countCounts[0]==window);
			if(verify){
				for(int c : counts){assert(c==0);}
				for(int i=1; i<countCounts.length; i++){assert(countCounts[i]==0);}
			}
			
			final int mask=(k>15 ? -1 : ~((-1)<<(2*k)));
			int current=0;
			//int ns=0;
			int kmer=0, kmer2=0;
			
			double entropySum=0;
			int entropyMeasurements=0;
			
			for(int i=0, i2=-window; i2<bases.length; i++, i2++){
				
//				System.err.println("\nStart: i="+i+", current="+current+", ns="+ns+"\n"+Arrays.toString(counts)+"\n"+Arrays.toString(countCounts));
				
				if(i<bases.length){
					byte b=bases[i];
					if(!AminoAcid.isFullyDefined(b)){
//						ns++;
						b='A';
					}
					final int n=Dedupe.baseToNumber[b];
					kmer=((kmer<<2)|n)&mask;
					
					if(counts[kmer]<1){
						assert(counts[kmer]==0);
						current++;
					}
					countCounts[counts[kmer]]--;
					assert(countCounts[counts[kmer]]>=-1): i+", "+current+", "+Tools.cardinality(counts)+"\n"+Arrays.toString(counts)+"\n"+Arrays.toString(countCounts);
					counts[kmer]++;
					assert(counts[kmer]<=window+1) : Arrays.toString(counts)+"\n"+Arrays.toString(countCounts);
					countCounts[counts[kmer]]++;
					if(verify){
						assert(current==Tools.cardinality(counts)) : current+", "+Tools.cardinality(counts)+"\n"+Arrays.toString(counts);
						assert(Tools.sum(countCounts)>0 && (Tools.sum(countCounts)<=window+1)): current+", "+Tools.cardinality(counts)+"\n"+Arrays.toString(countCounts);
					}
					
//					System.err.println("Added "+kmer+"; counts["+kmer+"]="+counts[kmer]);
				}
				
				if(i2>=0){
					byte b2=bases[i2];
					if(!AminoAcid.isFullyDefined(b2)){
//						ns--;
						b2='A';
					}
					final int n2=Dedupe.baseToNumber[b2];
					kmer2=((kmer2<<2)|n2)&mask;
					
					countCounts[counts[kmer2]]--;
					assert(countCounts[counts[kmer2]]>=0);
					counts[kmer2]--;
					countCounts[counts[kmer2]]++;
					if(counts[kmer2]<1){
						assert(counts[kmer2]==0) : Arrays.toString(counts);
						current--;
					}
					if(verify){
						assert(current==Tools.cardinality(counts)) : current+", "+Tools.cardinality(counts)+"\n"+Arrays.toString(counts);
						assert(Tools.sum(countCounts)>=0 && (Tools.sum(countCounts)<=window)): current+", "+Tools.cardinality(counts)+"\n"+Arrays.toString(countCounts);
					}
					
//					System.err.println("Removed "+kmer2+"; count="+counts[kmer2]);
				}
				
				if(verify && i2>-1 && i<bases.length){
					assert(Tools.sum(counts)==window);
					assert(Tools.sum(countCounts)==window): current+", "+Tools.cardinality(counts)+"\n"+Arrays.toString(countCounts);
				}
				
				if(i2>=-1 && i<bases.length){
					 float e=calcEntropy(countCounts, window, kmerspace);
					 entropySum+=e;
					 entropyMeasurements++;
				}
			}

//			System.err.println(" *** ");
//			System.err.println(entropySum+", "+entropyMeasurements+", "+(entropySum/(Tools.max(1, entropyMeasurements))));
//			System.err.println(window+", "+k+", "+kmerspace+", "+counts.length+", "+countCounts.length);
//			System.err.println(" *** ");
			
			return (float)(entropySum/(Tools.max(1, entropyMeasurements)));
		}
		
		/**
		 * Calculate the entropy of the countCounts for a particular window.
		 * @param countCounts element i holds the number of unique kmers occurring i times in the window.
		 * @param window Window length.
		 * @param kmerspace 2^k
		 * @return Entropy
		 */
		private float calcEntropy(short[] countCounts, int window, int kmerspace){
			double sum=0;
			for(int i=1; i<countCounts.length; i++){
				int cc=countCounts[i];
				double pklogpk=entropy[i];
				sum+=(cc*pklogpk);
			}
//			System.err.println("sum = "+sum);
//			System.err.println("entropy = "+(sum*entropyMult));
			return (float)(sum*entropyMult);
		}
		
		/*--------------------------------------------------------------*/
		
		/** Input read stream */
		private final ConcurrentReadInputStream cris;
		/** Output read streams */
		private final ConcurrentReadOutputStream ros, rosb, ross;
		
		private final ReadStats readstats;
		private final int[] overlapVector;
		private final int[] countArray;
		
		private final IntList idList;
		private final IntList countList;
		
		//These "*T" fields are used to store counts on a per-thread basis.
		
		long[] hitCountsT;
		long[] scaffoldReadCountsT;
		long[] scaffoldBaseCountsT;
		
		final short[] entropyCounts;
		final short[] entropyCountCounts;
		
		private float[] aprob, bprob;
		
		private long readsInT=0;
		private long basesInT=0;
		private long readsOutuT=0;
		private long basesOutuT=0;
		
		private long readsOutmT=0;
		private long basesOutmT=0;

		private final long maxBasesOutmT;
		private final long maxBasesOutuT;
		
		private long readsQTrimmedT=0;
		private long basesQTrimmedT=0;
		private long readsFTrimmedT=0;
		private long basesFTrimmedT=0;
		private long readsQFilteredT=0;
		private long basesQFilteredT=0;
		private long readsEFilteredT=0;
		private long basesEFilteredT=0;

		private long readsKTrimmedT=0;
		private long basesKTrimmedT=0;
		private long readsKFilteredT=0;
		private long basesKFilteredT=0;
		
		private long readsTrimmedByOverlapT=0;
		private long basesTrimmedByOverlapT=0;
		
		private long badGcBasesT=0;
		private long badGcReadsT=0;
		
	}
	
	/*--------------------------------------------------------------*/
	
	/**
	 * Object holding a String and numbers, for tracking the number of read and base hits per sequence.
	 */
	private static class StringNum implements Comparable<StringNum>{
		
		public StringNum(String name_, int len_, long reads_, long bases_){
			name=name_;
			length=len_;
			reads=reads_;
			bases=bases_;
		}
		public final int compareTo(StringNum o){
			if(bases!=o.bases){return o.bases>bases ? 1 : -1;}
			if(reads!=o.reads){return o.reads>reads ? 1 : -1;}
			return name.compareTo(o.name);
		}
		public final boolean equals(StringNum o){
			return compareTo(o)==0;
		}
		public final String toString(){
			return name+"\t"+length+"\t"+reads+"\t"+bases;
		}
		
		/*--------------------------------------------------------------*/
		
		/** Sequence name */
		public final String name;
		/** Sequence length */
		public final int length;
		/** Number of reads or bases mapped to this sequence */
		public final long reads, bases;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------        Static Methods        ----------------*/
	/*--------------------------------------------------------------*/
	
	/** Current available memory */
	private static final long freeMemory(){
		Runtime rt=Runtime.getRuntime();
		return rt.freeMemory();
	}
	
	/**
	 * Transforms a kmer into a canonical value stored in the table.  Expected to be inlined.
	 * @param kmer Forward kmer
	 * @param rkmer Reverse kmer
	 * @param lengthMask Bitmask with single '1' set to left of kmer
	 * @return Canonical value
	 */
	private final long toValue(long kmer, long rkmer, long lengthMask){
		assert(lengthMask==0 || (kmer<lengthMask && rkmer<lengthMask)) : lengthMask+", "+kmer+", "+rkmer;
		long value=(rcomp ? Tools.max(kmer, rkmer) : kmer);
		return (value&middleMask)|lengthMask;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------            Fields            ----------------*/
	/*--------------------------------------------------------------*/
	
	/** For calculating kmer cardinality */
	private final LogLog loglog;
	
	/** Has this class encountered errors while processing? */
	public boolean errorState=false;
	
	/** Fraction of available memory preallocated to arrays */
	private double preallocFraction=1.0;
	/** Initial size of data structures */
	private int initialSize=-1;
	
	/** Hold kmers.  A kmer X such that X%WAYS=Y will be stored in keySets[Y] */
	private final AbstractKmerTable[] keySets;
	/** A scaffold's name is stored at scaffoldNames.get(id).  
	 * scaffoldNames[0] is reserved, so the first id is 1. */
	private final ArrayList<String> scaffoldNames=new ArrayList<String>();
	/** Names of reference files (refNames[0] is valid). */
	private final ArrayList<String> refNames=new ArrayList<String>();
	/** Number of scaffolds per reference. */
	private final int[] refScafCounts;
	/** scaffoldCounts[id] stores the number of reads with kmer matches to that scaffold */ 
	private AtomicLongArray scaffoldReadCounts;
	/** scaffoldBaseCounts[id] stores the number of bases with kmer matches to that scaffold */ 
	private AtomicLongArray scaffoldBaseCounts;
	/** Set to false to force threads to share atomic counter arrays. */ 
	private boolean ALLOW_LOCAL_ARRAYS=true;
	/** scaffoldLengths[id] stores the length of that scaffold */ 
	private IntList scaffoldLengths=new IntList();
	/** hitCounts[x] stores the number of reads with exactly x kmer matches */
	private long[] hitCounts;
	/** Array of reference files from which to load kmers */
	private String[] ref=null;
	/** Array of literal strings from which to load kmers */
	private String[] literal=null;

	/** Input reads */
	private String in1=null, in2=null;
	/** Input qual files */
	private String qfin1=null, qfin2=null;
	/** Output reads (unmatched and at least minlen) */
	private String out1=null, out2=null;
	/** Output reads (matched or shorter than minlen) */
	private String outb1=null, outb2=null;
	/** Output reads whose mate was discarded */
	private String outsingle=null;
	/** Statistics output files */
	private String outstats=null, outrqc=null, outrpkm=null, outrefstats=null;
	@Deprecated
	/** duk-style statistics */
	private String outduk=null;
	
	/** Optional file for quality score recalibration */
	private String samFile=null;
	
	/** Dump kmers here. */
	private String dump=null;

	/** Quit after this many bases written to outm */
	private long maxBasesOutm=-1;
	/** Quit after this many bases written to outu */
	private long maxBasesOutu=-1;
	
	/** Maximum input reads (or pairs) to process.  Does not apply to references.  -1 means unlimited. */
	private long maxReads=-1;
	/** Process this fraction of input reads. */
	private float samplerate=1f;
	/** Set samplerate seed to this value. */
	private long sampleseed=-1;
	
	/** Output reads in input order.  May reduce speed. */
	private final boolean ORDERED;
	/** Attempt to match kmers shorter than normal k on read ends when doing kTrimming. */
	private boolean useShortKmers=false;
	/** Make the middle base in a kmer a wildcard to improve sensitivity */
	private boolean maskMiddle=true;
	
	/** Store reference kmers with up to this many substitutions */
	private int hammingDistance=0;
	/** Search for query kmers with up to this many substitutions */
	private int qHammingDistance=0;
	/** Store reference kmers with up to this many edits (including indels) */
	private int editDistance=0;
	/** Store short reference kmers with up to this many substitutions */
	private int hammingDistance2=-1;
	/** Search for short query kmers with up to this many substitutions */
	private int qHammingDistance2=-1;
	/** Store short reference kmers with up to this many edits (including indels) */
	private int editDistance2=-1;
	/** Never skip more than this many consecutive kmers when hashing reference. */
	private int maxSkip=1;
	/** Always skip at least this many consecutive kmers when hashing reference.
	 * 1 means every kmer is used, 2 means every other, etc. */
	private int minSkip=1;
	
	/** Trim this much extra around matched kmers */
	private int trimPad;
	
	/*--------------------------------------------------------------*/
	/*----------------        Entropy Fields        ----------------*/
	/*--------------------------------------------------------------*/
	
	/** Kmer length for entropy calculation */
	private int entropyK=5;
	/** Window length for entropy calculation */
	private int entropyWindow=50;
	/** Minimum entropy to be considered "complex", on a scale of 0-1 */
	private float entropyCutoff=-1;
	/** Verify consistency of related data structures (slow) */
	private boolean verifyEntropy=false;

	/** Perform entropy calculation */
	private final boolean calcEntropy;
	/** Number of possible unique kmers */
	private final int entropyKmerspace;
	/** A precalculated constant */
	private final double entropyMult;
	/** Array of precalculated constants */
	private final double[] entropy;
	
	/*--------------------------------------------------------------*/
	/*----------------          Statistics          ----------------*/
	/*--------------------------------------------------------------*/
	
	long readsIn=0;
	long basesIn=0;
	long readsOut=0;
	long basesOut=0;
	
	long readsQTrimmed=0;
	long basesQTrimmed=0;
	long readsFTrimmed=0;
	long basesFTrimmed=0;
	long readsQFiltered=0;
	long basesQFiltered=0;
	long readsEFiltered=0;
	long basesEFiltered=0;
	
	long readsKTrimmed=0;
	long basesKTrimmed=0;
	long readsKFiltered=0;
	long basesKFiltered=0;
	
	long badGcReads;
	long badGcBases;
	
	long readsTrimmedByOverlap;
	long basesTrimmedByOverlap;
	
	long refReads=0;
	long refBases=0;
	long refKmers=0;
	
	public long modsum=0; //123
	
	long storedKmers=0;
	
	/*--------------------------------------------------------------*/
	/*----------------       Final Primitives       ----------------*/
	/*--------------------------------------------------------------*/
	
	/** Don't look for kmers in read 1 */
	private final boolean skipR1;
	/** Don't look for kmers in read 2 */
	private final boolean skipR2;
	/** Correct errors via read overlap */
	private final boolean ecc;
	
	/** Look for reverse-complements as well as forward kmers.  Default: true */
	private final boolean rcomp;
	/** Don't allow a read 'N' to match a reference 'A'.  
	 * Reduces sensitivity when hdist>0 or edist>0.  Default: false. */
	private final boolean forbidNs;
	/** AND bitmask with 0's at the middle base */ 
	private final long middleMask;
	/** Use HashForest data structure */
	private final boolean useForest;
	/** Use KmerTable data structure */
	private final boolean useTable;
	/** Use HashArray data structure (default) */
	private final boolean useArray;	
	
	/** Normal kmer length */
	private final int k;
	/** k-1; used in some expressions */
	private final int k2;
	/** Emulated kmer greater than k */
	private final int kbig;
	/** Effective kmer size */
	private final int keff;
	/** Shortest kmer to use for trimming */
	private final int mink;
	/** A read may contain up to this many kmers before being considered a match.  Default: 0 */
	private final int maxBadKmers0;
	/** A read must share at least this fraction of its kmers to be considered a match.  Default: 0 */
	private final float minKmerFraction;
	/** Reference kmers must cover at least this fraction of read bases to be considered a match.  Default: 0 */
	private final float minCoveredFraction;
	
	/** Recalibrate quality scores using matrices */
	private final boolean recalibrateQuality;
	/** Quality-trim the left side */
	private final boolean qtrimLeft;
	/** Quality-trim the right side */
	private final boolean qtrimRight;
	/** Trim bases at this quality or below.  Default: 4 */
	private final byte trimq;
	/** Throw away reads below this average quality before trimming.  Default: 0 */
	private final byte minAvgQuality;
	/** If positive, calculate average quality from the first X bases only.  Default: 0 */
	private final int minAvgQualityBases;
	/** Throw away reads failing chastity filter (:Y: in read header) */
	private final boolean chastityFilter;
	/** Crash if a barcode is encountered that contains Ns or is not in the table */
	private final boolean failBadBarcodes;
	/** Remove reads with Ns in barcodes or that are not in the table */
	private final boolean removeBadBarcodes;
	/** Fail reads missing a barcode */
	private final boolean failIfNoBarcode;
	/** A set of valid barcodes; null if unused */
	private final HashSet<String> barcodes;
	/** Throw away reads containing more than this many Ns.  Default: -1 (disabled) */
	private final int maxNs;
	/** Throw away reads containing without at least this many consecutive called bases. */
	private int minConsecutiveBases=0;
	/** Throw away reads containing fewer than this fraction of any particular base. */
	private final float minBaseFrequency;
	/** Throw away reads shorter than this after trimming.  Default: 10 */
	private final int minReadLength;
	/** Throw away reads longer than this after trimming.  Default: Integer.MAX_VALUE */
	private final int maxReadLength;
	/** Toss reads shorter than this fraction of initial length, after trimming */
	private final float minLenFraction;
	/** Filter reads by whether or not they have matching kmers */
	private final boolean kfilter;
	/** Trim matching kmers and all bases to the left */
	private final boolean ktrimLeft;
	/** Trim matching kmers and all bases to the right */
	private final boolean ktrimRight;
	/** Don't trim, but replace matching kmers with a symbol (default N) */
	private final boolean ktrimN;
	/** Exclude kmer itself when ktrimming */
	private final boolean ktrimExclusive;
	/** Replace bases covered by matched kmers with this symbol */
	private final byte trimSymbol;
	/** Convert masked bases to lowercase */
	private final boolean kmaskLowercase;
	/** Only mask fully-covered bases **/
	private final boolean kmaskFullyCovered;
	/** Output over-trimmed reads to outbad (outmatch).  If false, they are discarded. */
	private final boolean addTrimmedToBad;
	/** Find the sequence that shares the most kmer matches when filtering. */
	private final boolean findBestMatch;
	/** Trim pairs to the same length, when adapter-trimming */
	private final boolean trimPairsEvenly;
	/** Trim left bases of the read to this position (exclusive, 0-based) */
	private final int forceTrimLeft;
	/** Trim right bases of the read after this position (exclusive, 0-based) */
	private final int forceTrimRight;
	/** Trim this many rightmost bases of the read */
	private final int forceTrimRight2;
	/** Trim right bases of the read modulo this value. 
	 * e.g. forceTrimModulo=50 would trim the last 3bp from a 153bp read. */
	private final int forceTrimModulo;
	
	/** Discard reads with GC below this. */
	private final float minGC;
	/** Discard reads with GC above this. */
	private final float maxGC;
	/** Discard reads outside of GC bounds. */
	private final boolean filterGC;
	/** Average GC for paired reads. */
	private final boolean usePairGC;
	
	/** If positive, only look for kmer matches in the leftmost X bases */
	private int restrictLeft;
	/** If positive, only look for kmer matches the rightmost X bases */
	private int restrictRight;
	
	/** True iff java was launched with the -ea' flag */
	private final boolean EA;
	/** Skip this many initial input reads */
	private final long skipreads;

	/** Pairs go to outbad if either of them is bad, as opposed to requiring both to be bad.
	 * Default: true. */
	private final boolean removePairsIfEitherBad;
	
	/** Print only statistics for scaffolds that matched at least one read 
	 * Default: true. */ 
	private final boolean printNonZeroOnly;
	
	/** Rename reads to indicate what they matched.
	 * Default: false. */ 
	private final boolean rename;
	/** Use names of reference files instead of scaffolds.
	 * Default: false. */ 
	private final boolean useRefNames;
	
	/** Fraction of kmers to skip, 0 to 15 out of 16 */
	private final int speed;
	
	/** Skip this many kmers when examining the read.  Default 1.
	 * 1 means every kmer is used, 2 means every other, etc. */
	private final int qSkip;
	
	/** noAccel is true if speed and qSkip are disabled, accel is the opposite. */
	private final boolean noAccel, accel;
	
	/*--------------------------------------------------------------*/
	/*----------------         BBMerge Flags        ----------------*/
	/*--------------------------------------------------------------*/
	
	/** Trim implied adapters based on overlap, for reads with insert size shorter than read length */
	private final boolean trimByOverlap;
	private final boolean useQualityForOverlap;
	private final boolean strictOverlap;
	
	private int minOverlap0=7;
	private int minOverlap=14;
	private int minInsert0=16;
	private int minInsert=40;
	
	private final float maxRatio;
	private final float ratioMargin;
	private final float ratioOffset;
	private final float efilterRatio;
	private final float efilterOffset;
	private final float pfilterRatio;
	private final float meeFilter;
	
	/*--------------------------------------------------------------*/
	/*----------------        Histogram Flags       ----------------*/
	/*--------------------------------------------------------------*/
	
	private final boolean MAKE_QUALITY_ACCURACY;
	private final boolean MAKE_QUALITY_HISTOGRAM;
	private final boolean MAKE_MATCH_HISTOGRAM;
	private final boolean MAKE_BASE_HISTOGRAM;
	
	private final boolean MAKE_EHIST;
	private final boolean MAKE_INDELHIST;
	private final boolean MAKE_LHIST;
	private final boolean MAKE_GCHIST;
	private final boolean MAKE_IDHIST;
	
	/*--------------------------------------------------------------*/
	/*----------------         Static Fields        ----------------*/
	/*--------------------------------------------------------------*/
	
	/** Number of tables (and threads, during loading) */ 
	private static final int WAYS=7; //123
	/** Default initial size of data structures */
	private static final int initialSizeDefault=128000;
	/** Verbose messages */
	public static final boolean verbose=false; //123
	
	/** Print messages to this stream */
	private static PrintStream outstream=System.err;
	/** Permission to overwrite existing files */
	public static boolean overwrite=true;
	/** Permission to append to existing files */
	public static boolean append=false;
	/** Print speed statistics upon completion */
	public static boolean showSpeed=true;
	/** Display progress messages such as memory usage */
	public static boolean DISPLAY_PROGRESS=true;
	/** Number of ProcessThreads */
	public static int THREADS=Shared.threads();
	/** Indicates end of input stream */
	private static final ArrayList<Read> POISON=new ArrayList<Read>(0);
	/** Number of columns for statistics output, 3 or 5 */
	public static int STATS_COLUMNS=3;
	/** Release memory used by kmer storage after processing reads */
	public static boolean RELEASE_TABLES=true;
	/** Max value of hitCount array */
	public static final int HITCOUNT_LEN=1000;
	/** Make unambiguous copies of ref sequences with ambiguous bases */
	public static boolean REPLICATE_AMBIGUOUS=false;
	
	/** x&clearMasks[i] will clear base i */
	private static final long[] clearMasks;
	/** x|setMasks[i][j] will set base i to j */
	private static final long[][] setMasks;
	/** x&leftMasks[i] will clear all bases to the right of i (exclusive) */
	private static final long[] leftMasks;
	/** x&rightMasks[i] will clear all bases to the left of i (inclusive) */
	private static final long[] rightMasks;
	/** x|kMasks[i] will set the bit to the left of the leftmost base */
	private static final long[] lengthMasks;
	
	public static HashMap<String,String> RQC_MAP=null;
	
	/*--------------------------------------------------------------*/
	/*----------------      Static Initializers     ----------------*/
	/*--------------------------------------------------------------*/
	
	static{
		clearMasks=new long[32];
		leftMasks=new long[32];
		rightMasks=new long[32];
		lengthMasks=new long[32];
		setMasks=new long[4][32];
		for(int i=0; i<32; i++){
			clearMasks[i]=~(3L<<(2*i));
		}
		for(int i=0; i<32; i++){
			leftMasks[i]=((-1L)<<(2*i));
		}
		for(int i=0; i<32; i++){
			rightMasks[i]=~((-1L)<<(2*i));
		}
		for(int i=0; i<32; i++){
			lengthMasks[i]=((1L)<<(2*i));
		}
		for(int i=0; i<32; i++){
			for(long j=0; j<4; j++){
				setMasks[(int)j][i]=(j<<(2*i));
			}
		}
	}
	
}
