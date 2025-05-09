#!/bin/bash
#tadpole in=<infile>

usage(){
echo "
Written by Brian Bushnell
Last modified November 18, 2015

Description:  Uses kmer counts to assembles contigs, extend sequences, 
or error-correct reads.  Tadpole has no upper bound for kmer length,
but some values are not supported.  Specifically, it allows 1-31,
multiples of 2 from 32-62, multiples of 3 from 63-93, etc.

Usage:
Generation:   tadpole.sh in=<reads> out=<contigs>
Extension:    tadpole.sh in=<reads> ine=<sequences> oute=<extended> mode=extend
Correction:   tadpole.sh in=<reads> out=<corrected> mode=correct

Input may be fasta or fastq, compressed or uncompressed.


Input parameters:
in=<file>           Primary input file for reads to use as kmer data.
in2=<file>          Second input file for paired data.
ine=<file>          Primary input file for sequences to extend.
ine2=<file>         Second input file for paired reads.
reads=-1            Only process this number of reads, then quit (-1 means all).

Output parameters:
out=<file>          Write contigs (in contig mode).
oute=<file>         Write extended reads (in extend mode).
ihist=<file>        Write insert size histogram (in insert mode).
dump=<file>         Write kmers and their counts.
fastadump=t         Write kmers and counts as fasta versus 2-column tsv.
mincounttodump=1    Only dump kmers with at least this depth.
showstats=t         Print assembly statistics after writing contigs.

Prefiltering parameters:
prefilter=0         If set to a positive integer, use a countmin sketch
                    to ignore kmers with depth of that value or lower.
prehashes=2         Number of hashes for prefilter.
prefiltersize=0.2   (pff) Fraction of memory to use for prefilter.
minprobprefilter=t  (mpp) Use minprob for the prefilter.
prepasses=1         Use this many prefiltering passes; higher be more thorough
                    if the filter is very full.  Set to 'auto' to iteratively 
                    prefilter until the remaining kmers will fit in memory.
onepass=f           If true, prefilter will be generated in same pass as kmer
                    counts.  Much faster but counts will be lower, by up to
                    prefilter's depth limit.

Hashing parameters:
k=31                Kmer length (1 to infinity).  Memory use increases with K.
prealloc=t          Pre-allocate memory rather than dynamically growing; 
                    faster and more memory-efficient.  A float fraction (0-1)
                    may be specified; default is 1.
minprob=0.5         Ignore kmers with overall probability of correctness below this.
minprobmain=t       (mpm) Use minprob for the primary kmer counts.
threads=X           Spawn X hashing threads (default is number of logical processors).
rcomp=t             Store and count each kmer together and its reverse-complement.

Assembly parameters:
mincountseed=3      (mcs) Minimum kmer count to seed a new contig or begin extension.
mincountextend=2    (mce) Minimum kmer count continue extension of a read or contig.
mincountretain=0    (mincr) Discard kmers with count below this.
maxcountretain=INF  (maxcr) Discard kmers with count above this.
branchmult1=20      (bm1) Min ratio of 1st to 2nd-greatest path depth at high depth.
branchmult2=3       (bm2) Min ratio of 1st to 2nd-greatest path depth at low depth.
branchlower=3       (blc) Max value of 2nd-greatest path depth to be considered low.
minextension=1      (mine) Do not keep contigs that did not extend at least this much.
mincontig=100       (minc) Do not write contigs shorter than this.
mincoverage=1       (mincov) Do not write contigs with average coverage below this.
trimends=0          (trim) Trim contig ends by this much.  Trimming by K/2 
                    may yield more accurate genome size estimation.
contigpasses=16     Build contigs with decreasing seed depth for this many iterations.
contigpassmult=1.7  Ratio between seed depth of two iterations.
ownership=auto      For concurrency; do not touch.

Processing modes:
mode=contig         contig: Make contigs from kmers.
                    extend: Extend sequences to be longer, and optionally
                            perform error correction.
                    correct: Error correct only.
                    insert: Measure insert sizes.

Extension parameters:
extendleft=100      (el) Extend to the left by at most this many bases.
extendright=100     (er) Extend to the right by at most this many bases.
ibb=t               (ignorebackbranches) Do not stop at backward branches.

Error-correction parameters:
ecc=f               Error correct via kmer counts.
pincer=t            If ecc is enabled, use the pincer algorithm.
tail=t              If ecc is enabled, use the tail algorithm.
aggressive=f        (aecc) Correct with bidirectional double-pass.
markbadbases=0      (mbb) Any base fully covered by kmers with count
                    below this will be changed to N.
markdeltaonly=t     (mdo) Only mark bad bases adjacent to good bases.
errormult1=60       (em1) Min ratio between kmer depths to call an error.
errormult2=3        (em2) Alternate ratio between kmer depths.
errorlowerconst=3   (elc) Use mult2 when the lower kmer is at most this deep.
mincountcorrect=4   (mcc) Don't correct to kmers with count under this.
pathsimilarityfraction=0.3 (psf) Max difference ratio considered similar.
                           Controls whether a path appears to be continuous.
pathsimilarityconstant=3   (psc) Absolute differences below this are ignored.
errorextensionpincer=5     (eep) Verify this many additional bases after the
                           correction as matching current bases, for pincer.
errorextensiontail=9       (eet) Verify this many additional bases after the
                           correction as matching current bases, for tail.

Shaving parameters:
shave=f             Remove dead ends (aka hair).
rinse=f             Remove bubbles.
maxshavedepth=1     (msd) Shave or rinse kmers at most this deep.
maxshavedepth=1     (msd) Shave or rinse kmers at most this deep.
exploredist=100     (sed) Quit after exploring this far.
discardlength=150   (sdl) Discard shavings up to this long.


Overlap parameters (for overlapping paired-end reads only):
merge=f             Attempt to merge reads before counting kmers.
ecco=f              Error correct via overlap, but do not merge reads.

Java Parameters:
-Xmx                This will be passed to Java to set memory usage, overriding the program's automatic memory detection.
                    -Xmx20g will specify 20 gigs of RAM, and -Xmx200m will specify 200 megs.  The max is typically 85% of physical memory.
"
}

pushd . > /dev/null
DIR="${BASH_SOURCE[0]}"
while [ -h "$DIR" ]; do
  cd "$(dirname "$DIR")"
  DIR="$(readlink "$(basename "$DIR")")"
done
cd "$(dirname "$DIR")"
DIR="$(pwd)/"
popd > /dev/null

#DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/"
CP="$DIR""current/"

z="-Xmx14g"
z2="-Xms14g"
EA="-ea"
set=0

if [ -z "$1" ] || [[ $1 == -h ]] || [[ $1 == --help ]]; then
	usage
	exit
fi

calcXmx () {
	source "$DIR""/calcmem.sh"
	parseXmx "$@"
	if [[ $set == 1 ]]; then
		return
	fi
	freeRam 15000m 84
	z="-Xmx${RAM}m"
	z2="-Xms${RAM}m"
}
calcXmx "$@"

tadpole() {
	if [[ $NERSC_HOST == genepool ]]; then
		module unload oracle-jdk
		module load oracle-jdk/1.7_64bit
		module load pigz
	fi
	local CMD="java $EA $z $z2 -cp $CP assemble.Tadpole $@"
	echo $CMD >&2
	eval $CMD
}

tadpole "$@"
