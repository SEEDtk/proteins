/**
 *
 */
package org.theseed.proteins.kmers.reps;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.kohsuke.args4j.Argument;
import org.theseed.utils.ICommand;

/**
 * This script is used when an incremental update is being made to the genome database.  It takes as input
 * evaluation results for new genomes and folds them into existing results.  For a complete rerun of all
 * evaluation results, use the GenomeProcessor class instead.
 *
 * The positional parameters are the name of the output directory, the name of the input file, and the
 * name of the original input directory.  The input directory must contain the repXX.list.tbl files listing
 * the representatives of each genome, the PhenTrnaSyntAlph.fa file containing the seed protein DNA, and
 * the repXX.ser files containing the representative genome protein sequences.  This information is used
 * to preload the ProteinData records and initialize the RepGenomeDb objects.  The new genomes are then
 * read in and the updated files written to the output directory.
 *
 * The following command-line options are supported.
 *
 * -v	show more detailed log messages
 * -b	batch size for PATRIC queries
 *
 * @author Bruce Parrello
 *
 */
public class UpdateProcessor extends BaseGenomeProcessor implements ICommand {

    @Argument(index = 2, metaVar = "inDir", usage = "input directory with existing genome files", required = true)
    private File inDir;

    @Override
    protected void setDefaults() {
        this.setBatchSize(500);
    }

    @Override
    protected boolean validateParms() throws IOException {
        this.checkParms();
        if (! this.inDir.isDirectory())
            throw new FileNotFoundException("Input directory " + this.inDir + " is not found or invalid.");
        return true;
    }

    @Override
    public void runCommand() {
        try {
            // Read all the genomes from the input file.
            ProteinDataFactory genomeList = initializeProteinData();
            // Read all the old genomes from the input directory.
            genomeList.restoreData(this, this.inDir);
            // We need to create the FASTA files for the seed protein list and the
            // binning BLAST database.  We do that here.
            createFastaFiles();
            // Sort the genomes into repgen sets.
            collateGenomes();
            // Save all the repgen sets.
            saveRepGenSets();
            // Write out the protein Fasta file for the first set.  This is used to find
            // seed proteins.
            writeSeedProt();
            // Assign genomes to repgen sets.
            log.info("Assigning genomes to repgen sets.");
            writeListFiles();
            // Now we write the protein FASTA files and the stats files.
            writeProteinFasta();
            // Now we produce the repFinder file used to find close genomes.
            writeRepFinder();
            log.info("All done.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}