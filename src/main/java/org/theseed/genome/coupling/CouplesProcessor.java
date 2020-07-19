/**
 *
 */
package org.theseed.genome.coupling;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Genome;
import org.theseed.genome.GenomeDirectory;
import org.theseed.io.TabbedLineReader;
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
 * -m	minimum number of genomes for a pair to be output (default 10)
 * -n	neighbor algorithm (default ADJACENT)
 *
 * --format		report format (default SCORES)
 * --verify		output of a previous coupling run used to guide the verification report
 * --skip		if specified, the name of a tab-delimited file (with headers) whose first columns contains class
 * 				IDs to be ignored
 *
 * @author Bruce Parrello
 *
 */
public class CouplesProcessor extends BaseCouplingProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(CouplesProcessor.class);
    /** hash of pairs to genome sets */
    private Map<FeatureClass.Pair, Set<String>> pairMap;
    /** blacklist of bad class IDs */
    private Set<String> blackList;

    // COMMAND-LINE OPTIONS

    /** report type */
    @Option(name = "--format", aliases = { "--output", "--outFmt" }, usage = "output format")
    private CouplingReporter.Type reportType;

    /** previous output to be verified (format = VERIFY only) */
    @Option(name = "--verify", metaVar = "couplings.tbl", usage = "previous output to be verified (format = VERIFY only)")
    private File oldOutput;

    /** minimum group size */
    @Option(name = "-m", aliases = { "--min" }, metaVar = "100", usage = "minimum group size for output to the report")
    private int minGroup;

    /** file containing prohibited class IDs */
    @Option(name = "--skip", aliases = { "--black", "--blackList" }, metaVar = "invalid.tbl",
            usage = "if specified, a file containing bad class IDs")
    private File blackListFile;

    /** input directory */
    @Argument(index = 0, metaVar = "genomeDir", usage = "input genome directory", required = true)
    private File genomeDir;


    // We make this public to enable testing of neighbor finders.
    @Override
    public void setDefaults() {
        this.reportType = CouplingReporter.Type.SCORES;
        this.minGroup = 10;
        this.oldOutput = null;
        this.blackListFile = null;
        this.setDefaultConfiguration();
    }

    @Override
    protected boolean validateParms() throws IOException {
        if (this.minGroup < 1)
            throw new IllegalArgumentException("Invalid minimum group size.  Must be at least 1.");
        if (! this.genomeDir.isDirectory())
            throw new FileNotFoundException("Specified genome directory" + this.genomeDir + " is not found or invalid.");
        this.validateConfiguration();
        // Create the pair map.
        this.pairMap = new HashMap<FeatureClass.Pair, Set<String>>(100000);
        // Check for a blacklist.
        this.blackList = new HashSet<String>();
        if (this.blackListFile != null) {
            if (! this.blackListFile.canRead())
                throw new FileNotFoundException("Blacklist file " + this.blackListFile + " is not found or unreadable.");
            else try (TabbedLineReader blackStream = new TabbedLineReader(this.blackListFile)) {
                for (TabbedLineReader.Line line : blackStream)
                    this.blackList.add(line.get(0));
                log.info("{} blacklisted class IDs found in {}.", this.blackList.size(), this.blackListFile);
            }
        }
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
                // Loop through the results.  For each one, we check subsequent results up to the gap
                // distance.
                int n = gResults.size() - 1;
                int pairCount = 0;
                for (int i = 0; i < n; i++) {
                    FeatureClass.Result resI = gResults.get(i);
                    Collection<FeatureClass.Result> neighbors = finder.getNeighbors(gResults, i);
                    // Here we have two neighboring features, so we pair the classes.
                    for (String classI : resI) {
                        if (this.blackList.contains(classI))
                            blacklisted++;
                        else {
                            for (FeatureClass.Result resJ : neighbors)
                                for (String classJ : resJ) {
                                    if (! this.blackList.contains(classJ) && ! classI.contentEquals(classJ)) {
                                        FeatureClass.Pair pair = classifier.new Pair(classI, classJ);
                                        Set<String> pairList = this.pairMap.computeIfAbsent(pair, k -> new HashSet<String>(5));
                                        pairList.add(genome.getId());
                                        pairCount++;
                                    }
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
            for (Map.Entry<FeatureClass.Pair, Set<String>> pairData : this.pairMap.entrySet()) {
                Set<String> gSet = pairData.getValue();
                // Only use the group if it is big enough.
                if (gSet.size() < this.minGroup) {
                    skipCount++;
                } else {
                    reporter.writePair(pairData.getKey(), gSet);
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

}
