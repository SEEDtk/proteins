/**
 *
 */
package org.theseed.genome.coupling;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Genome;
import org.theseed.genome.GenomeDirectory;
import org.theseed.reports.CouplingReporter;

/**
 * This command computes functional couplings using genomes in a specific genome directory.  It allows specification of
 * the method for computing the basis of the coupling and the type of reporting.  The default is to couple by
 * protein families and output a list of the genomes where the coupling is found.
 *
 * The positional parameter is the name of the directory containing the input genomes.
 *
 * The command-line options are as follows.
 *
 * -h	display command usage
 * -v	display more detailed status messages
 * -d	maximum acceptable distance for features to be considered neighbors (default 5000)
 * -t	classification method for features (default PROTFAM)
 * -m	minimum weighted/total number of genomes for a pair to be output (default 15.0; group filters WEIGHT AND SIZE only)
 * -n	neighbor algorithm (default ADJACENT)
 * -f	type of class filter (default NONE)
 * -p	type of pair filter (default WEIGHT)
 *
 * --format		report format (default SCORES)
 * --verify		output of a previous coupling run used to guide the verification report
 * --skip		if specified, the name of a tab-delimited file (with headers) whose first columns contains class
 * 				IDs to be ignored (class filter BLACKLIST only)
 * --limit		if specified, the maximum number of occurrences of a class allowed in a genome; more frequently-
 * 				occurring classes are filtered out (class filter LIMITED only)
 * --include	if specified, the name of a tab-delimited file (with headers) whose first column contains class
 * 				IDs to be included in the output (group filter WHITELIST only)
 * --names		FASTA file containing family names (classification type PROTFAM only)
 *
 * @author Bruce Parrello
 *
 */
public class CouplesProcessor extends BaseCouplingProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(CouplesProcessor.class);
    /** hash of pairs to genome sets and weights */
    private Map<FeatureClass.Pair, FeatureClass.PairData> pairMap;
    /** class-filtering algorithm */
    private ClassFilter classFilter;
    /** pair-filtering algorithm */
    private PairFilter pairFilter;

    // COMMAND-LINE OPTIONS

    /** report type */
    @Option(name = "--format", aliases = { "--output", "--outFmt" }, usage = "output format")
    private CouplingReporter.Type reportType;

    /** previous output to be verified (format = VERIFY only) */
    @Option(name = "--verify", metaVar = "couplings.tbl", usage = "previous output to be verified (format = VERIFY only)")
    private File oldOutput;

    /** minimum group size */
    @Option(name = "-m", aliases = { "--min" }, metaVar = "20.0", usage = "minimum group size/weight for output to the report (pair filter types SIZE and WEIGHT)")
    private double minWeight;

    /** class filter algorithm */
    @Option(name = "-f", aliases = { "--filter" }, usage = "class filtering algorithm")
    private ClassFilter.Type classFilterType;

    /** group/pair filter algorithm */
    @Option(name = "-p", aliases = { "--pairFilter", "--groupFilter" }, usage = "pair/group filtering algorithm")
    private PairFilter.Type pairFilterType;

    /** file containing prohibited class IDs */
    @Option(name = "--skip", aliases = { "--black", "--blackList" }, metaVar = "invalid.tbl",
            usage = "if specified, a file containing bad class IDs (class filter type BLACKLIST)")
    private File blackListFile;

    /** maximum number of class occurrences per genome */
    @Option(name = "--limit", aliases = { "--maxPerGenome" }, metaVar = "5",
            usage = "maximum number of class occurrences per genome (class filter type LIMITED)")
    private int classLimit;

    /** whitelist file for pair-filtering */
    @Option(name = "--include", metaVar = "classIdFile.tbl", usage = "if specified, a file containing required class IDs (pair filter type WHITELIST)")
    private File whiteGroupFile;

    /** FASTA file containing family names (PROTFAM only) */
    @Option(name = "--names", metaVar = "families.fa", usage = "if specified, a FASTA file containing family IDs and names (class type PROTFAM only)")
    private File nameFastaFile;

    /** input directory */
    @Argument(index = 0, metaVar = "genomeDir", usage = "input genome directory", required = true)
    private File genomeDir;


    // We make this public to enable testing of neighbor finders.
    @Override
    public void setDefaults() {
        this.reportType = CouplingReporter.Type.SCORES;
        this.minWeight = 15.0;
        this.oldOutput = null;
        this.blackListFile = null;
        this.whiteGroupFile = null;
        this.classLimit = 2;
        this.classFilterType = ClassFilter.Type.NONE;
        this.pairFilterType = PairFilter.Type.WEIGHT;
        this.nameFastaFile = null;
        this.setDefaultConfiguration();
    }

    @Override
    protected boolean validateParms() throws IOException {
        if (this.minWeight < 1)
            throw new IllegalArgumentException("Invalid minimum group size.  Must be at least 1.");
        if (! this.genomeDir.isDirectory())
            throw new FileNotFoundException("Specified genome directory" + this.genomeDir + " is not found or invalid.");
        this.validateConfiguration();
        // Create the pair map.
        this.pairMap = new HashMap<FeatureClass.Pair, FeatureClass.PairData>(100000);
        // Create the filters.
        this.classFilter = this.classFilterType.create(this);
        log.info("Class filtering type is {}.", this.classFilter);
        this.pairFilter = this.pairFilterType.create(this);
        log.info("Pair filtering type is {}.", this.pairFilter);
        return true;
    }

    @Override
    protected void runCommand() throws Exception {
        log.info("Initializing report.");
        // Start by creating the reporter object.
        try (CouplingReporter reporter = this.reportType.create(System.out, this)) {
            // Initialize the report.
            reporter.writeHeader();
            // Get the classifier and finder.
            FeatureClass classifier = this.getClassifier();
            NeighborFinder finder = this.getFinder();
            // Check for a name file.
            if (classifier instanceof FamilyFeatureClass && this.nameFastaFile != null)
                ((FamilyFeatureClass) classifier).cacheNames(this.nameFastaFile);
            // Open the genome directory.
            log.info("Scanning genome directory {}.", this.genomeDir);
            GenomeDirectory genomes = new GenomeDirectory(this.genomeDir);
            int total = genomes.size();
            log.info("{} genomes found.", total);
            // Loop through the genomes, filling the pair map.
            int count = 0;
            int blacklisted = 0;
            for (Genome genome : genomes) {
                count++;
                log.info("Processing genome {} of {}: {}.", count, total, genome);
                List<FeatureClass.Result> gResults = classifier.getResults(genome);
                log.info("{} classifiable features found.", gResults.size());
                blacklisted += this.classFilter.apply(gResults);
                // Loop through the results.  For each one, we check subsequent results up to the gap
                // distance.
                int n = gResults.size() - 1;
                int pairCount = 0;
                for (int i = 0; i < n; i++) {
                    FeatureClass.Result resI = gResults.get(i);
                    Collection<FeatureClass.Result> neighbors = finder.getNeighbors(gResults, i);
                    // Here we have two neighboring features, so we pair the classes.
                    for (String classI : resI) {
                        for (FeatureClass.Result resJ : neighbors)
                            for (String classJ : resJ) {
                                if (! classI.contentEquals(classJ)) {
                                    FeatureClass.Pair pair = classifier.new Pair(classI, classJ);
                                    double weight = resI.getWeight(classI) * resJ.getWeight(classJ);
                                    FeatureClass.PairData pairList = this.pairMap.computeIfAbsent(pair, k -> new FeatureClass.PairData());
                                    pairList.addGenome(genome.getId(), weight);
                                    pairCount++;
                                }
                            }
                    }
                }
                log.info("{} pairs found in {}.", pairCount, genome);
                // Register the genome with the report facility.
                reporter.register(genome);
            }
            // Now we produce the output.
            log.info("{} distinct pairs found in genome set.", this.pairMap.size());
            int outputCount = 0;
            int groupTotal = 0;
            int groupMax = 0;
            int skipCount = 0;
            count = 0;
            for (Map.Entry<FeatureClass.Pair, FeatureClass.PairData> pairEntry : this.pairMap.entrySet()) {
                FeatureClass.PairData gSet = pairEntry.getValue();
                FeatureClass.Pair pair = pairEntry.getKey();
                // Only use the group if it is big enough.
                if (! this.pairFilter.isSignificant(pair, gSet)) {
                    skipCount++;
                } else {
                    reporter.writePair(pair, gSet);
                    outputCount++;
                    if (gSet.size() > groupMax) groupMax = gSet.size();
                    groupTotal += gSet.size();
                }
                count++;
                if (count % 5000 == 0)
                    log.info("{} pairs processed for output. {} skipped.", outputCount, skipCount);
            }
            // Finish the report.
            log.info("Finishing report. {} pairs output, {} skippped, {} features blacklisted.", outputCount, skipCount,
                    blacklisted);
            if (outputCount > 0)
                log.info("Mean group size is {}. Max group size is {}.", (double) groupTotal / outputCount, groupMax);
            reporter.finish();
        }
    }

    /**
     * @return the old output file
     */
    public File getOldOutput() {
        return this.oldOutput;
    }

    /**
     * @return the file containing the blacklist
     */
    public File getBlackListFile() {
        return blackListFile;
    }

    /**
     * @return the class limit for the limit class-filter
     */
    public int getClassLimit() {
        return classLimit;
    }

    /**
     * @return the size/weight limit for the pair-filter
     */
    public double getGroupLimit() {
        return minWeight;
    }

    /**
     * @return the whitelist file name for the pair-filter
     */
    public File getWhiteGroupFile() {
        return this.whiteGroupFile;
    }

}
