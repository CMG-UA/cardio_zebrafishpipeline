package jgi;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import stream.ConcurrentGenericReadInputStream;
import stream.ConcurrentReadInputStream;
import stream.FASTQ;
import stream.FastaReadInputStream;
import stream.ConcurrentReadOutputStream;
import stream.KillSwitch;
import stream.Read;
import stream.SamLine;

import dna.Data;
import dna.Parser;
import dna.Timer;
import fileIO.ByteFile;
import fileIO.ByteFile1;
import fileIO.ByteFile2;
import fileIO.ReadWrite;
import fileIO.FileFormat;

import align2.ListNum;
import align2.ReadStats;
import align2.Shared;
import align2.Tools;
import align2.TrimRead;

/**
 * @author Brian Bushnell
 * @date Sep 11, 2012
 *
 */
public class ReformatReads {

	public static void main(String[] args){
		Timer t=new Timer();
		ReformatReads rr=new ReformatReads(args);
		rr.process(t);
	}
	
	public ReformatReads(String[] args){
		
		args=Parser.parseConfig(args);
		if(Parser.parseHelp(args, true)){
			printOptions();
			System.exit(0);
		}
		
		for(String s : args){if(s.startsWith("out=standardout") || s.startsWith("out=stdout")){outstream=System.err;}}
		outstream.println("Executing "+getClass().getName()+" "+Arrays.toString(args)+"\n");
		
		boolean setInterleaved=false; //Whether it was explicitly set.

		
		
		Shared.READ_BUFFER_LENGTH=Tools.min(200, Shared.READ_BUFFER_LENGTH);
		Shared.capBuffers(4);
		ReadWrite.USE_PIGZ=ReadWrite.USE_UNPIGZ=true;
		ReadWrite.MAX_ZIP_THREADS=Shared.threads();
		ReadWrite.ZIP_THREAD_DIVISOR=1;
		
		SamLine.SET_FROM_OK=true;
//		SamLine.CONVERT_CIGAR_TO_MATCH=true;
		
		Parser parser=new Parser();
		for(int i=0; i<args.length; i++){
			String arg=args[i];
			String[] split=arg.split("=");
			String a=split[0].toLowerCase();
			String b=split.length>1 ? split[1] : null;
			if(b==null || b.equalsIgnoreCase("null")){b=null;}
			while(a.startsWith("-")){a=a.substring(1);} //In case people use hyphens
			
			if(parser.parse(arg, a, b)){
				//do nothing
			}else if(a.equals("passes")){
				assert(false) : "'passes' is disabled.";
//				passes=Integer.parseInt(b);
			}else if(a.equals("path")){
				Data.setPath(b);
			}else if(a.equals("verbose")){
				verbose=Tools.parseBoolean(b);
				ByteFile1.verbose=verbose;
				ByteFile2.verbose=verbose;
				stream.FastaReadInputStream.verbose=verbose;
				ConcurrentGenericReadInputStream.verbose=verbose;
//				align2.FastaReadInputStream2.verbose=verbose;
				stream.FastqReadInputStream.verbose=verbose;
				ReadWrite.verbose=verbose;
			}else if(a.equals("sample") || a.equals("samplereads") || a.equals("samplereadstarget") || a.equals("srt")){
				sampleReadsTarget=Tools.parseKMG(b);
				sampleReadsExact=(sampleReadsTarget>0);
			}else if(a.equals("samplebases") || a.equals("samplebasestarget") || a.equals("sbt")){
				sampleBasesTarget=Tools.parseKMG(b);
				sampleBasesExact=(sampleBasesTarget>0);
			}else if(a.equals("addslash")){
				addslash=Tools.parseBoolean(b);
			}else if(a.equals("slashspace") || a.equals("spaceslash")){
				boolean x=Tools.parseBoolean(b);
				if(x){
					slash1=" /1";
					slash2=" /2";
				}else{
					slash1="/1";
					slash2="/2";
				}
			}else if(a.equals("addunderscore") || a.equals("underscore")){
				addunderscore=Tools.parseBoolean(b);
			}else if(a.equals("uniquenames")){
				uniqueNames=Tools.parseBoolean(b);
			}else if(a.equals("verifyinterleaved") || a.equals("verifyinterleaving") || a.equals("vint")){
				verifyinterleaving=Tools.parseBoolean(b);
			}else if(a.equals("verifypaired") || a.equals("verifypairing") || a.equals("vpair")){
				verifypairing=Tools.parseBoolean(b);
			}else if(a.equals("allowidenticalnames") || a.equals("ain")){
				allowIdenticalPairNames=Tools.parseBoolean(b);
			}else if(a.equals("rcompmate") || a.equals("rcm")){
				reverseComplimentMate=Tools.parseBoolean(b);
				outstream.println("Set RCOMPMATE to "+reverseComplimentMate);
			}else if(a.equals("rcomp") || a.equals("rc")){
				reverseCompliment=Tools.parseBoolean(b);
				outstream.println("Set RCOMP to "+reverseCompliment);
			}else if(a.equals("deleteempty") || a.equals("deletempty") || a.equals("delempty") || a.equals("def")){
				deleteEmptyFiles=Tools.parseBoolean(b);
			}else if(a.equals("mappedonly")){
				mappedOnly=Tools.parseBoolean(b);
			}else if(a.equals("unmappedonly")){
				unmappedOnly=Tools.parseBoolean(b);
			}else if(a.equals("requiredbits") || a.equals("rbits")){
				requiredBits=Tools.parseIntHexDecOctBin(b);
			}else if(a.equals("filterbits") || a.equals("fbits")){
				filterBits=Tools.parseIntHexDecOctBin(b);
			}else if(a.equals("primaryonly")){
				primaryOnly=Tools.parseBoolean(b);
			}else if(a.equals("remap1")){
				remap1=Tools.parseRemap(b);
			}else if(a.equals("remap2")){
				remap2=Tools.parseRemap(b);
			}else if(a.equals("remap")){
				remap1=remap2=Tools.parseRemap(b);
			}else if(a.equals("skipreads")){
				skipreads=Tools.parseKMG(b);
			}else if(a.equals("undefinedton") || a.equals("iupacton") || a.equals("itn")){
				iupacToN=Tools.parseBoolean(b);
			}else if(a.equals("quantize")){
				if(b==null || b.length()<1){b="t";}
				if(Character.isLetter(b.charAt(0))){
					quantizeQuality=Tools.parseBoolean(b);
				}else{
					quantizeQuality=true;
					quantizeArray=Tools.parseByteArray(b, ",");
				}
			}else if(parser.in1==null && i==0 && !arg.contains("=") && (arg.toLowerCase().startsWith("stdin") || new File(arg).exists())){
				parser.in1=arg;
			}else{
				System.err.println("Unknown parameter "+args[i]);
				assert(false) : "Unknown parameter "+args[i];
				//				throw new RuntimeException("Unknown parameter "+args[i]);
			}
		}
		
		{//Process parser fields
			Parser.processQuality();
			
			maxReads=parser.maxReads;
			samplerate=parser.samplerate;
			sampleseed=parser.sampleseed;
			
			overwrite=ReadStats.overwrite=parser.overwrite;
			append=ReadStats.append=parser.append;
			testsize=parser.testsize;
			trimBadSequence=parser.trimBadSequence;
			breakLength=parser.breakLength;
			stoptag=SamLine.MAKE_STOP_TAG;
			
			forceTrimModulo=parser.forceTrimModulo;
			forceTrimLeft=parser.forceTrimLeft;
			forceTrimRight=parser.forceTrimRight;
			forceTrimRight2=parser.forceTrimRight2;
			qtrimLeft=parser.qtrimLeft;
			qtrimRight=parser.qtrimRight;
			trimq=parser.trimq;
			minAvgQuality=parser.minAvgQuality;
			minAvgQualityBases=parser.minAvgQualityBases;
			chastityFilter=parser.chastityFilter;
			failBadBarcodes=parser.failBadBarcodes;
			removeBadBarcodes=parser.removeBadBarcodes;
			failIfNoBarcode=parser.failIfNoBarcode;
			barcodes=parser.barcodes;
			maxNs=parser.maxNs;
			minConsecutiveBases=parser.minConsecutiveBases;
			minReadLength=parser.minReadLength;
			maxReadLength=parser.maxReadLength;
			minLenFraction=parser.minLenFraction;
			requireBothBad=parser.requireBothBad;
			minGC=parser.minGC;
			maxGC=parser.maxGC;
			filterGC=(minGC>0 || maxGC<1);
			usePairGC=parser.usePairGC;
			tossJunk=parser.tossJunk;
			recalibrateQuality=parser.recalibrateQuality;

			setInterleaved=parser.setInterleaved;
			
			in1=parser.in1;
			in2=parser.in2;
			qfin1=parser.qfin1;
			qfin2=parser.qfin2;

			out1=parser.out1;
			out2=parser.out2;
			outsingle=parser.outsingle;
			qfout1=parser.qfout1;
			qfout2=parser.qfout2;
			
			extin=parser.extin;
			extout=parser.extout;
			
			loglog=(parser.loglog ? new LogLog(parser) : null);
		}
		
		if(recalibrateQuality){CalcTrueQuality.initializeMatrices();}
		
		if(SamLine.setxs && !SamLine.setintron){SamLine.INTRON_LIMIT=10;}
		qtrim=qtrimLeft||qtrimRight;
		
		if(in1!=null && in2==null && in1.indexOf('#')>-1 && !new File(in1).exists()){
			in2=in1.replace("#", "2");
			in1=in1.replace("#", "1");
		}
		if(out1!=null && out2==null && out1.indexOf('#')>-1){
			out2=out1.replace("#", "2");
			out1=out1.replace("#", "1");
		}
		
		if(out1!=null && in1!=null && out1.indexOf('%')>-1){
			out1=out1.replace("%", ReadWrite.stripExtension(in1));
		}
		if(out2!=null && out2.indexOf('%')>-1){
			if(in2!=null){
				out2=out2.replace("%", ReadWrite.stripExtension(in2));
			}else if(in1!=null){
				out2=out2.replace("%", ReadWrite.stripExtension(in1));
			}
		}
		
		if(in2!=null){
			if(FASTQ.FORCE_INTERLEAVED){System.err.println("Reset INTERLEAVED to false because paired input files were specified.");}
			FASTQ.FORCE_INTERLEAVED=FASTQ.TEST_INTERLEAVED=false;
		}
		
		if(verifyinterleaving || (verifypairing && in2==null)){
			verifypairing=true;
			setInterleaved=true;
//			if(FASTQ.FORCE_INTERLEAVED){System.err.println("Reset INTERLEAVED to false because paired input files were specified.");}
			FASTQ.FORCE_INTERLEAVED=FASTQ.TEST_INTERLEAVED=true;
		}
		
		assert(FastaReadInputStream.settingsOK());
		
		if(in1==null){
			printOptions();
			throw new RuntimeException("Error - at least one input file is required.");
		}
		if(!ByteFile.FORCE_MODE_BF1 && !ByteFile.FORCE_MODE_BF2 && Shared.threads()>2){
			ByteFile.FORCE_MODE_BF2=true;
		}
		
		if(out1==null){
			if(out2!=null){
				printOptions();
				throw new RuntimeException("Error - cannot define out2 without defining out1.");
			}
			if(!parser.setOut){
				System.err.println("No output stream specified.  To write to stdout, please specify 'out=stdout.fq' or similar.");
//				out1="stdout";
			}
		}
		
		if(!setInterleaved){
			assert(in1!=null && (out1!=null || out2==null)) : "\nin1="+in1+"\nin2="+in2+"\nout1="+out1+"\nout2="+out2+"\n";
			if(in2!=null){ //If there are 2 input streams.
				FASTQ.FORCE_INTERLEAVED=FASTQ.TEST_INTERLEAVED=false;
				outstream.println("Set INTERLEAVED to "+FASTQ.FORCE_INTERLEAVED);
			}else{ //There is one input stream.
				if(out2!=null){
					FASTQ.FORCE_INTERLEAVED=true;
					FASTQ.TEST_INTERLEAVED=false;
					outstream.println("Set INTERLEAVED to "+FASTQ.FORCE_INTERLEAVED);
				}
			}
		}
		
		if(out1!=null && out1.equalsIgnoreCase("null")){out1=null;}
		if(out2!=null && out2.equalsIgnoreCase("null")){out2=null;}
		if(outsingle!=null && outsingle.equalsIgnoreCase("null")){outsingle=null;}
		
		if(!Tools.testOutputFiles(overwrite, append, false, out1, out2, outsingle)){
			System.err.println((out1==null)+", "+(out2==null)+", "+out1+", "+out2);
			throw new RuntimeException("\n\noverwrite="+overwrite+"; Can't write to output files "+out1+", "+out2+"\n");
		}
		if(!Tools.testForDuplicateFiles(true, in1, in2, out1, out2, outsingle) || !ReadStats.testFiles(false)){
			throw new RuntimeException("Duplicate filenames are not allowed.");
		}
		
		FASTQ.PARSE_CUSTOM=parsecustom;
		
		ffout1=FileFormat.testOutput(out1, FileFormat.FASTQ, extout, true, overwrite, append, false);
		ffout2=FileFormat.testOutput(out2, FileFormat.FASTQ, extout, true, overwrite, append, false);
		ffoutsingle=FileFormat.testOutput(outsingle, FileFormat.FASTQ, extout, true, overwrite, append, false);

		ffin1=FileFormat.testInput(in1, FileFormat.FASTQ, extin, true, true);
		ffin2=FileFormat.testInput(in2, FileFormat.FASTQ, extin, true, true);

		assert(ReadStats.testFiles(true)) : "Existing output files specified, but overwrite==false";
		assert(ReadStats.testFiles(false)) : "Duplicate or output files specified";

//		System.err.println("\n"+ReadWrite.USE_PIGZ+", "+ReadWrite.USE_UNPIGZ+", "+Data.PIGZ()+", "+Data.UNPIGZ()+", "+ffin1+"\n");
//		assert(false) : ReadWrite.USE_PIGZ+", "+ReadWrite.USE_UNPIGZ+", "+Data.PIGZ()+", "+Data.UNPIGZ()+", "+ffin1;

		nameMap1=(uniqueNames ? new HashMap<String, Integer>() : null);
		nameMap2=(uniqueNames ? new HashMap<String, Integer>() : null);
		
		qualityRemapArray=makeQualityRemapArray(quantizeArray);
	}

	void process(Timer t){
		
		long readsRemaining=0;
		long basesRemaining=0;
		
		if(sampleReadsExact || sampleBasesExact){
			long[] counts=countReads(maxReads);
			readsRemaining=counts[0];
			basesRemaining=counts[2];
			setSampleSeed(sampleseed);
		}
		
		
		final ConcurrentReadInputStream cris;
		{
			useSharedHeader=(ffin1.samOrBam() && ffout1!=null && ffout1.samOrBam());
			cris=ConcurrentReadInputStream.getReadInputStream(maxReads, useSharedHeader, ffin1, ffin2, qfin1, qfin2);
			cris.setSampleRate(samplerate, sampleseed);
			if(verbose){System.err.println("Started cris");}
			cris.start(); //4567
		}
		boolean paired=cris.paired();
//		if(verbose){
			System.err.println("Input is being processed as "+(paired ? "paired" : "unpaired"));
//		}
		
		assert(!paired || breakLength<1) : "Paired input cannot be broken with 'breaklength'";

		final ConcurrentReadOutputStream ros;
		if(ffout1!=null){
			final int buff=4;
			
			if(cris.paired() && ffout2==null && ffout1!=null && !ffout1.samOrBam()){
				outstream.println("Writing interleaved.");
			}
			
			ros=ConcurrentReadOutputStream.getStream(ffout1, ffout2, qfout1, qfout2, buff, null, useSharedHeader);
			ros.start();
		}else{ros=null;}
		
		final ConcurrentReadOutputStream rosb;
		if(ffoutsingle!=null){
			final int buff=4;
			
			rosb=ConcurrentReadOutputStream.getStream(ffoutsingle, null, buff, null, useSharedHeader);
			rosb.start();
		}else{rosb=null;}
		final boolean discardTogether=(!paired || (ffoutsingle==null && !requireBothBad));
		
		long readsProcessed=0;
		long basesProcessed=0;
		
		//Only used with deleteEmptyFiles flag
		long readsOut1=0;
		long readsOut2=0;
		long readsOutSingle=0;
		
		long basesOut1=0;
		long basesOut2=0;
		long basesOutSingle=0;
		
		long basesFTrimmedT=0;
		long readsFTrimmedT=0;
		
		long basesQTrimmedT=0;
		long readsQTrimmedT=0;
		
		long lowqBasesT=0;
		long lowqReadsT=0;
		
		long badGcBasesT=0;
		long badGcReadsT=0;
		
		long readShortDiscardsT=0;
		long baseShortDiscardsT=0;
		
		long unmappedReadsT=0;
		long unmappedBasesT=0;
		
		long basesSwappedT=0;
		long readsSwappedT=0;

		final boolean MAKE_QHIST=ReadStats.COLLECT_QUALITY_STATS;
		final boolean MAKE_QAHIST=ReadStats.COLLECT_QUALITY_ACCURACY;
		final boolean MAKE_MHIST=ReadStats.COLLECT_MATCH_STATS;
		final boolean MAKE_BHIST=ReadStats.COLLECT_BASE_STATS;
		
		final boolean MAKE_EHIST=ReadStats.COLLECT_ERROR_STATS;
		final boolean MAKE_INDELHIST=ReadStats.COLLECT_INDEL_STATS;
		final boolean MAKE_LHIST=ReadStats.COLLECT_LENGTH_STATS;
		final boolean MAKE_GCHIST=ReadStats.COLLECT_GC_STATS;
		final boolean MAKE_IDHIST=ReadStats.COLLECT_IDENTITY_STATS;
		
		final ReadStats readstats=(MAKE_QHIST || MAKE_MHIST || MAKE_BHIST || MAKE_QAHIST || MAKE_EHIST || MAKE_INDELHIST || MAKE_LHIST || MAKE_GCHIST || MAKE_IDHIST) ? 
				new ReadStats() : null;
		
		{
			
			ListNum<Read> ln=cris.nextList();
			ArrayList<Read> reads=(ln!=null ? ln.list : null);
			
//			System.err.println("Fetched "+reads);
			
			if(reads!=null && !reads.isEmpty()){
				Read r=reads.get(0);
				assert((ffin1==null || ffin1.samOrBam()) || (r.mate!=null)==cris.paired());
			}

			while(reads!=null && reads.size()>0){
				
				if(skipreads>0){
					int removed=0;
					for(int i=0; i<reads.size(); i++){
						Read r=reads.get(i);
						if(r.numericID<skipreads){
							reads.set(i, null);
							removed++;
						}else{
							skipreads=-1;
							break;
						}
					}
					if(removed>0){
						Tools.condenseStrict(reads);
					}
				}
				
				ArrayList<Read> singles=(rosb==null ? null : new ArrayList<Read>(32));
				
				if(breakLength>0){
					breakReads(reads, breakLength, minReadLength);
				}
				
				for(int idx=0; idx<reads.size(); idx++){
					final Read r1=reads.get(idx);
					final Read r2=r1.mate;
					
					final int initialLength1=r1.length();
					final int initialLength2=(r1.mateLength());

					final int minlen1=(int)Tools.max(initialLength1*minLenFraction, minReadLength);
					final int minlen2=(int)Tools.max(initialLength2*minLenFraction, minReadLength);
					
					if(readstats!=null){
						if(MAKE_QHIST){readstats.addToQualityHistogram(r1);}
						if(MAKE_BHIST){readstats.addToBaseHistogram(r1);}
						if(MAKE_MHIST){readstats.addToMatchHistogram(r1);}
						if(MAKE_QAHIST){readstats.addToQualityAccuracy(r1);}

						if(MAKE_EHIST){readstats.addToErrorHistogram(r1);}
						if(MAKE_INDELHIST){readstats.addToIndelHistogram(r1);}
						if(MAKE_LHIST){readstats.addToLengthHistogram(r1);}
						if(MAKE_GCHIST){readstats.addToGCHistogram(r1);}
						if(MAKE_IDHIST){readstats.addToIdentityHistogram(r1);}
					}
					
					if(loglog!=null){loglog.hash(r1);}
					
					{
						readsProcessed++;
						basesProcessed+=initialLength1;
						if(reverseCompliment){r1.reverseComplement();}
					}
					if(r2!=null){
						readsProcessed++;
						basesProcessed+=initialLength2;
						if(reverseCompliment || reverseComplimentMate){r2.reverseComplement();}
					}
					
					if(verifypairing){
						String s1=r1==null ? null : r1.id;
						String s2=r2==null ? null : r2.id;
						boolean b=FASTQ.testPairNames(s1, s2, allowIdenticalPairNames);
						if(!b){
							outstream.println("Names do not appear to be correctly paired.\n"+s1+"\n"+s2+"\n");
							ReadWrite.closeStreams(cris, ros);
							System.exit(1);
						}
					}
					
					if(tossJunk){
						if(r1!=null && r1.junk()){
							lowqBasesT+=r1.length();
							lowqReadsT++;
							r1.setDiscarded(true);
						}
						if(r2!=null && r2.junk()){
							lowqBasesT+=r2.length();
							lowqReadsT++;
							r2.setDiscarded(true);
						}
					}
					
					if(iupacToN){
						if(r1!=null){r1.convertUndefinedTo((byte)'N');}
						if(r2!=null){r2.convertUndefinedTo((byte)'N');}
					}
					
					if(remap1!=null && r1!=null){
						int swaps=r1.remapAndCount(remap1);
						if(swaps>0){
							basesSwappedT+=swaps;
							readsSwappedT++;
						}
					}
					if(remap2!=null && r2!=null){
						int swaps=r2.remapAndCount(remap2);
						if(swaps>0){
							basesSwappedT+=swaps;
							readsSwappedT++;
						}
					}
					
					if(trimBadSequence){//Experimental
						if(r1!=null){
							int x=TrimRead.trimBadSequence(r1);
							basesQTrimmedT+=x;
							readsQTrimmedT+=(x>0 ? 1 : 0);
						}
						if(r2!=null){
							int x=TrimRead.trimBadSequence(r2);
							basesQTrimmedT+=x;
							readsQTrimmedT+=(x>0 ? 1 : 0);
						}
					}
					
					if(chastityFilter){
						if(r1!=null && r1.failsChastity()){
							lowqBasesT+=r1.length()+r1.mateLength();
							lowqReadsT+=1+r1.mateCount();
							r1.setDiscarded(true);
							if(r2!=null){r2.setDiscarded(true);}
						}
					}
					
					if(removeBadBarcodes){
						if(r1!=null && !r1.discarded() && r1.failsBarcode(barcodes, failIfNoBarcode)){
							if(failBadBarcodes){KillSwitch.kill("Invalid barcode detected: "+r1.id+"\nThis can be disabled with the flag barcodefilter=f");}
							lowqBasesT+=r1.length()+r1.mateLength();
							lowqReadsT+=1+r1.mateCount();
							r1.setDiscarded(true);
							if(r2!=null){r2.setDiscarded(true);}
						}
					}
					
					if(filterBits!=0 || requiredBits!=0){
						if(r1!=null && !r1.discarded()){
							assert(r1.obj!=null && r1.obj.getClass().equals(SamLine.class)) : "filterbits and requiredbits only work on sam/bam input.";
							SamLine sl=(SamLine)r1.obj;
							if(((sl.flag&filterBits)!=0) || ((sl.flag&requiredBits)!=requiredBits)){
								r1.setDiscarded(true);
								unmappedBasesT+=initialLength1;
								unmappedReadsT++;
							}
						}
						if(r2!=null && !r2.discarded()){
							assert(r2.obj!=null && r2.obj.getClass().equals(SamLine.class)) : "filterbits and requiredbits only work on sam/bam input.";
							SamLine sl=(SamLine)r2.obj;
							if(((sl.flag&filterBits)!=0) || ((sl.flag&requiredBits)!=requiredBits)){
								r2.setDiscarded(true);
								unmappedBasesT+=initialLength1;
								unmappedReadsT++;
							}
						}
					}
					
					if(SamLine.VERSION==1.3f){
						if(r1!=null && !r1.discarded()){
							assert(r1.obj!=null && r1.obj.getClass().equals(SamLine.class)) : "filterbits and requiredbits only work on sam/bam input.";
							SamLine sl=(SamLine)r1.obj;
							sl.cigar=SamLine.toCigar13(sl.cigar);
						}
						if(r2!=null && !r2.discarded()){
							assert(r2.obj!=null && r2.obj.getClass().equals(SamLine.class)) : "filterbits and requiredbits only work on sam/bam input.";
							SamLine sl=(SamLine)r2.obj;
							sl.cigar=SamLine.toCigar13(sl.cigar);
						}
					}
					
					if(stoptag){
						if(r1!=null && !r1.discarded()){
							assert(r1.obj!=null && r1.obj.getClass().equals(SamLine.class)) : "stoptag only works on sam/bam input.";
							SamLine sl=(SamLine)r1.obj;
							if(sl.mapped() && sl.cigar!=null){
								if(sl.optional==null){sl.optional=new ArrayList<String>(2);}
								sl.optional.add(SamLine.makeStopTag(sl.pos, sl.calcCigarLength(false, false), sl.cigar, r1.perfect()));
							}
						}
						if(r2!=null && !r2.discarded()){
							assert(r2.obj!=null && r2.obj.getClass().equals(SamLine.class)) : "stoptag only works on sam/bam input.";
							SamLine sl=(SamLine)r2.obj;
							if(sl.mapped() && sl.cigar!=null){
								if(sl.optional==null){sl.optional=new ArrayList<String>(2);}
								sl.optional.add(SamLine.makeStopTag(sl.pos, sl.calcCigarLength(false, false), sl.cigar, r2.perfect()));
							}
						}
					}
					
					if(mappedOnly){
						if(r1!=null && !r1.discarded() && (!r1.mapped() || r1.bases==null || r1.secondary())){
							r1.setDiscarded(true);
							unmappedBasesT+=initialLength1;
							unmappedReadsT++;
						}
						if(r2!=null && !r2.discarded() && (!r2.mapped() || r2.bases==null || r2.secondary())){
							r2.setDiscarded(true);
							unmappedBasesT+=initialLength2;
							unmappedReadsT++;
						}
					}else if(unmappedOnly){
						if(r1!=null && (r1.mapped() || r1.bases==null || r1.secondary())){
							r1.setDiscarded(true);
							unmappedBasesT+=initialLength1;
							unmappedReadsT++;
						}
						if(r2!=null && (r2.mapped() || r2.bases==null || r2.secondary())){
							r2.setDiscarded(true);
							unmappedBasesT+=initialLength2;
							unmappedReadsT++;
						}
					}
					
					if(primaryOnly){
						if(r1!=null && (r1.bases==null || r1.secondary())){
							r1.setDiscarded(true);
							unmappedBasesT+=initialLength1;
							unmappedReadsT++;
						}
						if(r2!=null && (r2.bases==null || r2.secondary())){
							r2.setDiscarded(true);
							unmappedBasesT+=initialLength2;
							unmappedReadsT++;
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
					
					if(recalibrateQuality){
						if(r1!=null && !r1.discarded()){
							CalcTrueQuality.recalibrate(r1);
						}
						if(r2!=null && !r2.discarded()){
							CalcTrueQuality.recalibrate(r2);
						}
					}
					
					if(quantizeQuality){
						final byte[] quals1=r1.quality, quals2=(r2==null ? null : r2.quality);
						if(quals1!=null){
							for(int i=0; i<quals1.length; i++){
								quals1[i]=qualityRemapArray[quals1[i]];
							}
						}
						if(quals2!=null){
							for(int i=0; i<quals2.length; i++){
								quals2[i]=qualityRemapArray[quals2[i]];
							}
						}
					}
					
					if(forceTrimLeft>0 || forceTrimRight>0 || forceTrimModulo>0 || forceTrimRight2>0){
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
					
					if(qtrim){
						if(r1!=null && !r1.discarded()){
							int x=TrimRead.trimFast(r1, qtrimLeft, qtrimRight, trimq, 1);
							basesQTrimmedT+=x;
							readsQTrimmedT+=(x>0 ? 1 : 0);
						}
						if(r2!=null && !r2.discarded()){
							int x=TrimRead.trimFast(r2, qtrimLeft, qtrimRight, trimq, 1);
							basesQTrimmedT+=x;
							readsQTrimmedT+=(x>0 ? 1 : 0);
						}
					}
					
					if(minAvgQuality>0){
						if(r1!=null && !r1.discarded() && r1.avgQuality(false, minAvgQualityBases)<minAvgQuality){
							lowqBasesT+=r1.length();
							lowqReadsT++;
							r1.setDiscarded(true);
						}
						if(r2!=null && !r2.discarded() && r2.avgQuality(false, minAvgQualityBases)<minAvgQuality){
							lowqBasesT+=r2.length();
							lowqReadsT++;
							r2.setDiscarded(true);
						}
					}
					
					if(maxNs>=0){
						if(r1!=null && !r1.discarded() && r1.countUndefined()>maxNs){
							lowqBasesT+=r1.length();
							lowqReadsT++;
							r1.setDiscarded(true);
						}
						if(r2!=null && !r2.discarded() && r2.countUndefined()>maxNs){
							lowqBasesT+=r2.length();
							lowqReadsT++;
							r2.setDiscarded(true);
						}
					}
					
					if(minConsecutiveBases>0){
						if(r1!=null && !r1.discarded() && !r1.hasMinConsecutiveBases(minConsecutiveBases)){
							lowqBasesT+=r1.length();
							lowqReadsT++;
							r1.setDiscarded(true);
						}
						if(r2!=null && !r2.discarded() && !r2.hasMinConsecutiveBases(minConsecutiveBases)){
							lowqBasesT+=r2.length();
							lowqReadsT++;
							r2.setDiscarded(true);
						}
					}
					
					if(minlen1>0 || minlen2>0 || maxReadLength>0){
//						assert(false) : minlen1+", "+minlen2+", "+maxReadLength+", "+r1.length();
						if(r1!=null && !r1.discarded()){
							int rlen=r1.length();
							if(rlen<minlen1 || (maxReadLength>0 && rlen>maxReadLength)){
								r1.setDiscarded(true);
								readShortDiscardsT++;
								baseShortDiscardsT+=rlen;
							}
						}
						if(r2!=null && !r2.discarded()){
							int rlen=r2.length();
							if(rlen<minlen1 || (maxReadLength>0 && rlen>maxReadLength)){
								r2.setDiscarded(true);
								readShortDiscardsT++;
								baseShortDiscardsT+=rlen;
							}
						}
					}
					
					boolean remove=false;
					if(r2==null){
						remove=r1.discarded();
					}else{
						remove=requireBothBad ? (r1.discarded() && r2.discarded()) : (r1.discarded() || r2.discarded());
					}
					
					if(remove){reads.set(idx, null);}
					else if(uniqueNames || addunderscore || addslash){
						
						if(r1.id==null){r1.id=""+r1.numericID;}
						if(r2!=null && r2.id==null){r2.id=r1.id;}
						
						if(uniqueNames){
							
							{
								Integer v=nameMap1.get(r1.id);
								if(v==null){
									nameMap1.put(r1.id, 1);
								}else{
									v++;
									nameMap1.put(r1.id, v);
									r1.id=r1.id+"_"+v;
								}
							}
							if(r2!=null){
								Integer v=nameMap2.get(r2.id);
								if(v==null){
									nameMap2.put(r2.id, 1);
								}else{
									v++;
									nameMap2.put(r2.id, v);
									r2.id=r2.id+"_"+v;
								}
							}
						}
						if(addunderscore){
							r1.id=Tools.whitespace.matcher(r1.id).replaceAll("_");
							if(r2!=null){r2.id=Tools.whitespace.matcher(r2.id).replaceAll("_");}
						}
						if(addslash){
							if(!r1.id.contains(slash1)){r1.id+=slash1;}
							if(r2!=null){
								if(!r2.id.contains(slash2)){r2.id+=slash2;}
							}
						}
					}
					
					if(singles!=null){
						if(r1.discarded() || (r2!=null && r2.discarded())){
							if(!r1.discarded()){
								Read r=r1.clone();
								r.mate=null;
								r.setPairnum(0);
								singles.add(r);
							}else if(r2!=null && !r2.discarded()){
								Read r=r2.clone();
								r.mate=null;
								r.setPairnum(0);
								singles.add(r);
							}
						}
					}
				}
				
				final ArrayList<Read> listOut;
				
//				assert(false) : sampleReadsExact+", "+sampleBasesExact;
				if(sampleReadsExact || sampleBasesExact){
					listOut=new ArrayList<Read>();
					if(sampleReadsExact){
						for(Read r : reads){
							if(r!=null){
								assert(readsRemaining>0) : readsRemaining;
								double prob=sampleReadsTarget/(double)(readsRemaining);
//								System.err.println("sampleReadsTarget="+sampleReadsTarget+", readsRemaining="+readsRemaining+", prob="+prob);
								if(randy.nextDouble()<prob){
									listOut.add(r);
									sampleReadsTarget--;
								}
							}
							readsRemaining--;
						}
					}else if(sampleBasesExact){
						for(Read r : reads){
							if(r!=null){
								assert(basesRemaining>0) : basesRemaining;
								int bases=r.length()+(r.mate==null ? 0 : r.mateLength());
								double prob=sampleBasesTarget/(double)(basesRemaining);
								if(randy.nextDouble()<prob){
									listOut.add(r);
									sampleBasesTarget-=bases;
								}
								basesRemaining-=bases;
							}
						}
					}
				}else{
					listOut=reads;
				}
//				if(deleteEmptyFiles){
					for(Read r : listOut){
						if(r!=null){
							readsOut1++;
							basesOut1+=r.length();
							if(r.mate!=null){
								readsOut2++;
								basesOut2+=r.mateLength();
							}
						}
					}
					if(singles!=null){
						for(Read r : singles){
							if(r!=null){
								readsOutSingle++;
								basesOutSingle+=r.length();
							}
						}
					}
//				}
				if(ros!=null){ros.add(listOut, ln.id);}
				if(rosb!=null){rosb.add(singles, ln.id);}

				cris.returnList(ln.id, false);
				ln=cris.nextList();
				reads=(ln!=null ? ln.list : null);
			}
			if(ln!=null){
//				cris.returnList(ln.id, ln.list==null || ln.list.isEmpty());
				assert(ln.list.isEmpty());
				cris.returnList(ln.id, true);
			}
		}
		
		errorState|=ReadStats.writeAll();
		
		errorState|=ReadWrite.closeStreams(cris, ros, rosb);
		
		if(deleteEmptyFiles){
			deleteEmpty(readsOut1, readsOut2, readsOutSingle);
		}
		
//		System.err.println(cris.errorState()+", "+(ros==null ? "null" : (ros.errorState()+", "+ros.finishedSuccessfully())));
//		if(ros!=null){
//			ReadStreamWriter rs1=ros.getRS1();
//			ReadStreamWriter rs2=ros.getRS2();
//			System.err.println(rs1==null ? "null" : rs1.finishedSuccessfully());
//			System.err.println(rs2==null ? "null" : rs2.finishedSuccessfully());
//		}
		
		t.stop();
		
		double rpnano=readsProcessed/(double)(t.elapsed);
		double bpnano=basesProcessed/(double)(t.elapsed);

		String rpstring=(readsProcessed<100000 ? ""+readsProcessed : readsProcessed<100000000 ? (readsProcessed/1000)+"k" : (readsProcessed/1000000)+"m");
		String bpstring=(basesProcessed<100000 ? ""+basesProcessed : basesProcessed<100000000 ? (basesProcessed/1000)+"k" : (basesProcessed/1000000)+"m");

		while(rpstring.length()<8){rpstring=" "+rpstring;}
		while(bpstring.length()<8){bpstring=" "+bpstring;}
		
		final long rawReadsIn=cris.readsIn(), rawBasesIn=cris.basesIn();
		final double rmult=100.0/rawReadsIn, bmult=100.0/rawBasesIn;
		final double rpmult=100.0/readsProcessed, bpmult=100.0/basesProcessed;
		
		outstream.println("Input:                  \t"+cris.readsIn()+" reads          \t"+
				cris.basesIn()+" bases");
		if(samplerate!=1f){
			outstream.println("Processed:              \t"+readsProcessed+" reads          \t"+
					basesProcessed+" bases");
		}
		
		if(remap1!=null || remap2!=null){
			outstream.println("Base Transforms:        \t"+readsSwappedT+" reads ("+String.format("%.2f",readsSwappedT*rpmult)+"%) \t"+
					basesSwappedT+" bases ("+String.format("%.2f",basesSwappedT*bpmult)+"%)");
		}
		if(qtrim || trimBadSequence){
			outstream.println("QTrimmed:               \t"+readsQTrimmedT+" reads ("+String.format("%.2f",readsQTrimmedT*rpmult)+"%) \t"+
					basesQTrimmedT+" bases ("+String.format("%.2f",basesQTrimmedT*bpmult)+"%)");
		}
		if(forceTrimLeft>0 || forceTrimRight>0 || forceTrimRight2>0 || forceTrimModulo>0){  
			outstream.println("FTrimmed:               \t"+readsFTrimmedT+" reads ("+String.format("%.2f",readsFTrimmedT*rpmult)+"%) \t"+
					basesFTrimmedT+" bases ("+String.format("%.2f",basesFTrimmedT*bpmult)+"%)");
		}
		if(minReadLength>0 || maxReadLength>0){
			outstream.println("Short Read Discards:    \t"+readShortDiscardsT+" reads ("+String.format("%.2f",readShortDiscardsT*rpmult)+"%) \t"+
					baseShortDiscardsT+" bases ("+String.format("%.2f",baseShortDiscardsT*bpmult)+"%)");
		}
		if(minAvgQuality>0 || maxNs>=0 || chastityFilter || tossJunk || removeBadBarcodes){
			outstream.println("Low quality discards:   \t"+lowqReadsT+" reads ("+String.format("%.2f",lowqReadsT*rpmult)+"%) \t"+
					lowqBasesT+" bases ("+String.format("%.2f",lowqBasesT*bpmult)+"%)");
		}
		if(filterGC){
			outstream.println("GC content discards:    \t"+badGcReadsT+" reads ("+String.format("%.2f",badGcReadsT*rpmult)+"%) \t"+
					badGcBasesT+" bases ("+String.format("%.2f",badGcBasesT*bpmult)+"%)");
		}
		final long ro=readsOut1+readsOut2+readsOutSingle, bo=basesOut1+basesOut2+basesOutSingle;
		outstream.println("Output:                 \t"+ro+" reads ("+String.format("%.2f",ro*rmult)+"%) \t"+
				bo+" bases ("+String.format("%.2f",bo*bmult)+"%)");
		
		if(loglog!=null){
			outstream.println("Unique "+loglog.k+"-mers:         \t"+loglog.cardinality());
		}
		
		outstream.println("\nTime:                         \t"+t);
		outstream.println("Reads Processed:    "+rpstring+" \t"+String.format("%.2fk reads/sec", rpnano*1000000));
		outstream.println("Bases Processed:    "+bpstring+" \t"+String.format("%.2fm bases/sec", bpnano*1000));
		if(testsize){
			long bytesProcessed=(new File(in1).length()+(in2==null ? 0 : new File(in2).length())+
					(qfin1==null ? 0 : new File(qfin1).length())+(qfin2==null ? 0 : new File(qfin2).length()));//*passes
			double xpnano=bytesProcessed/(double)(t.elapsed);
			String xpstring=(bytesProcessed<100000 ? ""+bytesProcessed : bytesProcessed<100000000 ? (bytesProcessed/1000)+"k" : (bytesProcessed/1000000)+"m");
			while(xpstring.length()<8){xpstring=" "+xpstring;}
			outstream.println("Bytes Processed:    "+xpstring+" \t"+String.format("%.2fm bytes/sec", xpnano*1000));
		}
		
		if(verifypairing){
			outstream.println("Names appear to be correctly paired.");
		}
		
		if(errorState){
			throw new RuntimeException("ReformatReads terminated in an error state; the output may be corrupt.");
		}
	}
	
	/*--------------------------------------------------------------*/
	
	private void deleteEmpty(long readsOut1, long readsOut2, long readsOutSingle){
		deleteEmpty(readsOut1, ffout1, qfout1);
		deleteEmpty(readsOut2, ffout2, qfout2);
		deleteEmpty(readsOutSingle, ffoutsingle, null);
	}
	
	private void deleteEmpty(long count, FileFormat ff, String qf){
		try {
			if(ff!=null && count<1){
				String s=ff.name();
				if(s!=null && !ff.stdio() && !ff.devnull()){
					File f=new File(ff.name());
					if(f.exists()){
						f.delete();
					}
				}
				if(qf!=null){
					File f=new File(qf);
					if(f.exists()){
						f.delete();
					}
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	

	public static void breakReads(ArrayList<Read> list, final int max, int min){
		if(!containsReadsOutsideSizeRange(list, min, max)){return;}
		assert(max>0 || min>0) : "min or max read length must be positive.";
		assert(max<1 || max>=min) : "max read length must be at least min read length: "+max+"<"+min;
		min=Tools.max(0, min);
		
		ArrayList<Read> temp=new ArrayList<Read>(list.size()*2);
		for(Read r : list){
			if(r==null || r.bases==null){
				temp.add(r);
			}else if(r.length()<min){
				temp.add(null);
			}else if(max<1 || r.length()<=max){
				temp.add(r);
			}else{
				final byte[] bases=r.bases;
				final byte[] quals=r.quality;
				final String name=r.id;
				final int limit=bases.length-min;
				for(int num=1, start=0, stop=max; start<limit; num++, start+=max, stop+=max){
					if(verbose){
						System.err.println(bases.length+", "+start+", "+stop);
						if(quals!=null){System.err.println(quals.length+", "+start+", "+stop);}
					}
					stop=Tools.min(stop, bases.length);
					byte[] b2=Arrays.copyOfRange(bases, start, stop);
					byte[] q2=(quals==null ? null : Arrays.copyOfRange(quals, start, stop));
					String n2=name+"_"+num;
					Read r2=new Read(b2, -1, -1, -1, n2, q2, r.numericID, r.flags);
					r2.setMapped(false);
					temp.add(r2);
				}
			}
		}
		list.clear();
		list.ensureCapacity(temp.size());
//		list.addAll(temp);
		for(Read r : temp){
			if(r!=null){list.add(r);}
		}
	}
	
	private static boolean containsReadsAboveSize(ArrayList<Read> list, int size){
		for(Read r : list){
			if(r!=null && r.bases!=null){
				if(r.length()>size){
					assert(r.mate==null) : "Read of length "+r.length()+">"+size+". Paired input is incompatible with 'breaklength'";
					return true;
				}
			}
		}
		return false;
	}
	
	private static boolean containsReadsOutsideSizeRange(ArrayList<Read> list, int min, int max){
		for(Read r : list){
			if(r!=null && r.bases!=null){
				if((max>0 && r.length()>max) || r.length()<min){
					assert(r.mate==null) : "Read of length "+r.length()+" outside of range "+min+"-"+max+". Paired input is incompatible with 'breaklength'";
					return true;
				}
			}
		}
		return false;
	}
	
	
	private long[] countReads(long maxReads){
		if(ffin1.stdio()){
			throw new RuntimeException("Can't precount reads from standard in, only from a file.");
		}
		
		final ConcurrentReadInputStream cris;
		{
			cris=ConcurrentReadInputStream.getReadInputStream(maxReads, true, ffin1, ffin2, null, null);
			if(verbose){System.err.println("Counting Reads");}
			cris.start(); //4567
		}
		
		ListNum<Read> ln=cris.nextList();
		ArrayList<Read> reads=(ln!=null ? ln.list : null);
		
		long count=0, count2=0, bases=0;
		
		while(reads!=null && reads.size()>0){
			count+=reads.size();
			for(Read r : reads){
				bases+=r.length();
				count2++;
				if(r.mate!=null){
					bases+=r.mateLength();
					count2++;
				}
			}
			cris.returnList(ln.id, ln.list.isEmpty());
			ln=cris.nextList();
			reads=(ln!=null ? ln.list : null);
		}
		cris.returnList(ln.id, ln.list.isEmpty());
		errorState|=ReadWrite.closeStream(cris);
		return new long[] {count, count2, bases};
	}
	
	public static final byte[] makeQualityRemapArray(byte[] quantizeArray) {
		byte[] array=new byte[128];
		for(int i=0; i<array.length; i++){
			byte q=0;
			for(byte x : quantizeArray){
				if(Tools.absdif(x, i)<=Tools.absdif(q, i)){q=x;}
			}
			array[i]=q;
		}
		return array;
	}
	
	private void printOptions(){
		outstream.println("Syntax:\n");
		outstream.println("java -ea -Xmx512m -cp <path> jgi.ReformatReads in=<infile> in2=<infile2> out=<outfile> out2=<outfile2>");
		outstream.println("\nin2 and out2 are optional.  \nIf input is paired and there is only one output file, it will be written interleaved.\n");
		outstream.println("Other parameters and their defaults:\n");
		outstream.println("overwrite=false  \tOverwrites files that already exist");
		outstream.println("ziplevel=4       \tSet compression level, 1 (low) to 9 (max)");
		outstream.println("interleaved=false\tDetermines whether input file is considered interleaved");
		outstream.println("fastawrap=70     \tLength of lines in fasta output");
		outstream.println("qin=auto         \tASCII offset for input quality.  May be set to 33 (Sanger), 64 (Illumina), or auto");
		outstream.println("qout=auto        \tASCII offset for output quality.  May be set to 33 (Sanger), 64 (Illumina), or auto (meaning same as input)");
		outstream.println("outsingle=<file> \t(outs) Write singleton reads here, when conditionally discarding reads from pairs.");
	}
	
	
	public void setSampleSeed(long seed){
		randy=new Random();
		if(seed>-1){randy.setSeed(seed);}
	}
	
	
	/*--------------------------------------------------------------*/
	
	private String in1=null;
	private String in2=null;
	
	private String qfin1=null;
	private String qfin2=null;

	private String out1=null;
	private String out2=null;
	private String outsingle=null;

	private String qfout1=null;
	private String qfout2=null;
	
	private String extin=null;
	private String extout=null;
	
	/*--------------------------------------------------------------*/
	
	/** For calculating kmer cardinality */
	private final LogLog loglog;
	
	/** Tracks names to ensure no duplicate names. */
	private final HashMap<String,Integer> nameMap1, nameMap2;
	private boolean uniqueNames=false;

	private boolean reverseComplimentMate=false;
	private boolean reverseCompliment=false;
	private boolean verifyinterleaving=false;
	private boolean verifypairing=false;
	private boolean allowIdenticalPairNames=true;
	private boolean trimBadSequence=false;
	private boolean chastityFilter=false;
	/** Crash if a barcode is encountered that contains Ns or is not in the table */
	private final boolean failBadBarcodes;
	/** Remove reads with Ns in barcodes or that are not in the table */
	private final boolean removeBadBarcodes;
	/** Fail reads missing a barcode */
	private final boolean failIfNoBarcode;
	/** A set of valid barcodes; null if unused */
	private final HashSet<String> barcodes;
	private boolean deleteEmptyFiles=false;
	private boolean mappedOnly=false;
	private boolean unmappedOnly=false;
	private boolean primaryOnly=false;
	/** For sam file filtering: These bits must be set. */
	private int requiredBits=0;
	/** For sam file filtering: These bits must be unset */
	private int filterBits=0;
	/** Add /1 and /2 to paired reads */
	private boolean addslash=false;
	/** Change read name whitespace to underscores */
	private boolean addunderscore=false;
	private String slash1=" /1";
	private String slash2=" /2";
	private boolean stoptag=false;
	private boolean iupacToN=false;
	

	private long maxReads=-1;
	private long skipreads=-1;
	private float samplerate=1f;
	private long sampleseed=-1;
	private boolean sampleReadsExact=false;
	private boolean sampleBasesExact=false;
	private long sampleReadsTarget=0;
	private long sampleBasesTarget=0;
	
	/** Recalibrate quality scores using matrices */
	private boolean recalibrateQuality=false;
	private boolean qtrimRight=false;
	private boolean qtrimLeft=false;
	private final int forceTrimLeft;
	private final int forceTrimRight;
	private final int forceTrimRight2;
	/** Trim right bases of the read modulo this value. 
	 * e.g. forceTrimModulo=50 would trim the last 3bp from a 153bp read. */
	private final int forceTrimModulo;
	private byte trimq=6;
	private byte minAvgQuality=0;
	private int minAvgQualityBases=0;
	private int maxNs=-1;
	private int minConsecutiveBases=0;
	private int breakLength=0;
	private int maxReadLength=0;
	private int minReadLength=0;
	private float minLenFraction=0;
	private float minGC=0;
	private float maxGC=1;
	private boolean filterGC=false;
	/** Average GC for paired reads. */
	private boolean usePairGC;
	private boolean tossJunk=false;
	/** Toss pair only if both reads are shorter than limit */ 
	private boolean requireBothBad=false;
	
	private boolean useSharedHeader;
	
	private byte[] remap1=null, remap2=null;
	
	private boolean quantizeQuality=false;
	
	private byte[] quantizeArray={0, 8, 13, 22, 27, 32, 37};
	private final byte[] qualityRemapArray;
	
	/*--------------------------------------------------------------*/
	
	private final FileFormat ffin1;
	private final FileFormat ffin2;

	private final FileFormat ffout1;
	private final FileFormat ffout2;
	private final FileFormat ffoutsingle;
	
	private final boolean qtrim;
	
	
	/*--------------------------------------------------------------*/
	
	private PrintStream outstream=System.err;
	public static boolean verbose=false;
	public boolean errorState=false;
	private boolean overwrite=false;
	private boolean append=false;
	private boolean parsecustom=false;
	private boolean testsize=false;
	
	private Random randy;
	
}
