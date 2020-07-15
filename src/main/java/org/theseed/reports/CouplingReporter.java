/**
 *
 */
package org.theseed.reports;

import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.theseed.genome.Genome;
import org.theseed.genome.coupling.FeatureClass;

/**
 * This is the base class for all reports produced by the coupling command.  Special consideration is taken to
 * allow subclasses to query for class names in batches, so the input lines are gathered in groups of 100 and
 * then an opportunity is given to the subclass to compute the names before we ask for them.
 *
 * @author Bruce Parrello
 *
 */
public abstract class CouplingReporter extends BaseReporter {

    // FIELDS
    /** current feature classifier */
    private FeatureClass classifier;
    /** queue of lines to write */
    private Map<FeatureClass.Pair, Collection<String>> lineQueue;
    /** queue of classes in the lines */
    private Set<String> classQueue;
    /** maximum size for queue */
    private static final int BATCH_SIZE = 100;

    /**
     * Construct a coupling report for output to a specified stream.
     *
     * @param output		output stream to receive the report.
     * @param classifier	feature classifier being used
     */
    public CouplingReporter(OutputStream output, FeatureClass classifier) {
        super(output);
        this.classifier = classifier;
        this.lineQueue = new HashMap<FeatureClass.Pair, Collection<String>>(BATCH_SIZE);
        this.classQueue = new HashSet<String>(BATCH_SIZE * 2);
    }

    /**
     * Start the report.
     */
    public void writeHeader() {
        this.println(this.classifier.getHeadings() + "\t" + this.getScoreHeadings());
    }

    /**
     * @return the headings for the score columns in this report
     * @return
     */
    protected abstract String getScoreHeadings();

    /**
     * Register any important data about a genome.
     *
     * @param genome	genome being processed for coupling
     */
    public abstract void register(Genome genome);

    /**
     * Write a single line of output.
     *
     * @param pair		classification pair found to be coupled
     * @param genomes	IDs of the genomes containing the coupling
     */
    public abstract void writePairLine(FeatureClass.Pair pair, Collection<String> genomes);

    /**
     * Process an output coupling.
     *
     * @param pair		classification pair found to be coupled
     * @param genomes	IDs of the genomes containing the coupling
     */
    public final void writePair(FeatureClass.Pair pair, Collection<String> genomes) {
        // Insure there is room for a new line.
        if (this.lineQueue.size() >= BATCH_SIZE) {
            this.processQueue();
            this.lineQueue.clear();
            this.classQueue.clear();
        }
        // Queue this line for the next batch.
        this.lineQueue.put(pair, genomes);
        this.classQueue.add(pair.getClass1());
        this.classQueue.add(pair.getClass2());
    }

    /**
     * Write out the queued lines.
     */
    private void processQueue() {
        // Insure we know all the class names in the queue.
        this.classifier.cacheNames(this.classQueue);
        // Write out the lines in the queue.
        for (Map.Entry<FeatureClass.Pair, Collection<String>> entry : this.lineQueue.entrySet()) {
            this.writePairLine(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Finish the report.  Here we flush out the last batch.
     */
    public final void finish() {
        this.processQueue();
    }



    /**
     * Enumeration of supported report types.
     */
    public static enum Type {
        GROUP, SCORES;

        public CouplingReporter create(OutputStream output, FeatureClass classifier) {
            CouplingReporter retVal = null;
            switch (this) {
            case GROUP:
                retVal = new GroupCouplingReporter(output, classifier);
                break;
            case SCORES:
                retVal = new ScoreCouplingReporter(output, classifier);
                break;
            }
            return retVal;
        }
    }

    /**
     * @return the classifier
     */
    public FeatureClass getClassifier() {
        return classifier;
    }

}
