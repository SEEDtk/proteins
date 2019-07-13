package org.theseed.proteins.kmers.reps;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.theseed.proteins.kmers.ProteinKmers;
import org.theseed.proteins.kmers.reps.RepGenome;
import org.theseed.proteins.kmers.reps.RepGenomeDb;
import org.theseed.sequence.FastaInputStream;
import org.theseed.sequence.FastaOutputStream;
import org.theseed.sequence.Sequence;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class AppTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AppTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( AppTest.class );
    }

    /**
     * Test the bug with the odd X-heavy protein not having a repeatable similarity.
     * @throws IOException
     */
    public void testXProtein() throws IOException {
        RepGenomeDb testdb = new RepGenomeDb(200);
        File inFile = new File("src/test", "xsmall.fa");
        FastaInputStream inStream = new FastaInputStream(inFile);
        Sequence testSeq = inStream.next();
        RepGenome testGenome = new RepGenome(testSeq);
        testdb.checkGenome(testGenome);
        RepGenomeDb.Representation result = testdb.findClosest(testSeq);
        assertTrue("Should not be an outlier.", result.getSimilarity() > 200);
        double distance = testGenome.distance(testGenome);
        assertEquals("Distance to self is nonzero.", 0.0, distance, 0.0);
        inStream.close();
    }


    /**
     * test protein kmers
     */
    public void testKmers() {
        ProteinKmers.setKmerSize(10);
        String myProt1 = "MGMLVPLISKISDLSEEAKACVAACSSVEELDEVRGRYIGRAGALTALLA"; // 50 AA
        String myProt2 = "MDINLFKEELEELAKKAKHMLNETASKNDLEQVKVSLLGKKGLLTLQSAA";
        String myProt3 = "MDINLFKEELKHMLNETASKKGLLTLQSA"; // 30 AA
        ProteinKmers kmer1 = new ProteinKmers(myProt1);
        ProteinKmers kmer2 = new ProteinKmers(myProt2);
        ProteinKmers kmer3 = new ProteinKmers(myProt3);
        assertEquals("Kmer1 has wrong protein.", myProt1, kmer1.getProtein());
        assertEquals("Kmer1 has wrong count.", 41, kmer1.size());
        assertEquals("Kmer1/kmer3 wrong similarity.", 3, kmer2.similarity(kmer3));
        assertEquals("Similarity not commutative.", 3, kmer3.similarity(kmer2));
        assertEquals("Kmer1 too close to kmer2.", 1.0, kmer1.distance(kmer2), 0.0);
        assertEquals("Kmer1 too close to kmer3.", 0.95, kmer2.distance(kmer3), 0.005);
    }

    /**
     * FASTA file test
     * @throws IOException
     */
    public void testFasta() throws IOException {
        File inFasta = new File("src/test", "test.fa");
        FastaInputStream inStream = new FastaInputStream(inFasta);
        ArrayList<Sequence> testSeqs = new ArrayList<Sequence>(5);
        for (Sequence input : inStream) {
            testSeqs.add(input);
        }
        inStream.close();
        assertEquals("Wrong number of sequences.", 5, testSeqs.size());
        Sequence seq = testSeqs.get(0);
        assertEquals("Wrong label for seq 1.", "label1", seq.getLabel());
        assertEquals("Wrong comment for seq 1.", "", seq.getComment());
        assertEquals("Wrong sequence for seq 1.", "tgtgcagcgagccctacagccttggagggaacaacacggactacctgccgctcgtctacccaaagggggtccccctccccaacacaacggttaccagcgtgccgagcg", seq.getSequence());
        seq = testSeqs.get(1);
        assertEquals("Wrong label for seq 2.", "label2", seq.getLabel());
        assertEquals("Wrong comment for seq 2.", "comment2 with spaces", seq.getComment());
        assertEquals("Wrong sequence for seq 2.", "ctcaatgggtccgtagtcggcatcggcagatgtgtataagcagcatgcccgccctctgcag", seq.getSequence());
        seq = testSeqs.get(2);
        assertEquals("Wrong label for seq 3.", "label3", seq.getLabel());
        assertEquals("Wrong comment for seq 3.", "comment3", seq.getComment());
        assertEquals("Wrong sequence for seq 3.", "gtataaagtattggcctgttgag", seq.getSequence());
        seq = testSeqs.get(3);
        assertEquals("Wrong label for seq 4.", "label4", seq.getLabel());
        assertEquals("Wrong comment for seq 4.", "comment4", seq.getComment());
        assertEquals("Wrong sequence for seq 4.", "", seq.getSequence());
        seq = testSeqs.get(4);
        assertEquals("Wrong label for seq 5.", "label5", seq.getLabel());
        assertEquals("Wrong comment for seq 5.", "comment5", seq.getComment());
        assertEquals("Wrong sequence for seq 5.", "ggggccctgaggtcctgagcaagtgggtcggcgagagcgagaaggcgataaggt", seq.getSequence());
        // Write the FASTA back out.
        File outFasta = new File("src/test", "fasta.ser");
        FastaOutputStream outStream = new FastaOutputStream(outFasta);
        outStream.write(testSeqs);
        outStream.close();
        // Read it in and verify.
        inStream = new FastaInputStream(outFasta);
        ArrayList<Sequence> readSeqs = new ArrayList<Sequence>(5);
        while (inStream.hasNext()) {
            readSeqs.add(inStream.next());
        }
        inStream.close();
        assertEquals("Wrong number of records on read back.", 5, readSeqs.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("Compare error at position " + i + " on readback.", testSeqs.get(i), readSeqs.get(i));
        }
    }

    /**
     * Test RepGenome object
     */
    public void testRepGenome() {
        String prot1 = "MSHLAELVASAKAAISQASDVAALDNVRVEYLGKKGHLTLQMTTLRELPPEERPAAGAVI" +
                "NEAKEQVQQALNARKAELESAALNARLAAETIDVSLPGRRIENGGLHPVTRTIDRIESFF" +
                "GELGFTVATGPEIEDDYHNFDALNIPGHHPARADHDTFWFDATRLLRTQTSGVQIRTMKA" +
                "QQPPIRIIAPGRVYRNDYDQTHTPMFHQMEGLIVDTNISFTNLKGTLHDFLRNFFEEDLQ" +
                "IRFRPSYFPFTEPSAEVDVMGKNGKWLEVLGCGMVHPNVLRNVGIDPEVYSGFAFGMGME" +
                "RLTMLRYGVTDLRSFFENDLRFLKQFK";
        RepGenome rep1 = new RepGenome("fig|1005530.3.peg.2208", "Escherichia coli EC4402", prot1);
        assertEquals("Incorrect genome ID parsed.", "1005530.3", rep1.getGenomeId());
        assertEquals("FID not stored.", "fig|1005530.3.peg.2208", rep1.getFid());
        assertEquals("Incorrect name stored.", "Escherichia coli EC4402", rep1.getName());
        assertEquals("Incorrect protein stored.", prot1, rep1.getProtein());
        RepGenome rep2 = new RepGenome("fig|1005530.4.peg.2208", "Escherichia coli EC4402 B", prot1);
        assertFalse("Different genome IDs are still equal.", rep1.equals(rep2));
        assertEquals("Incorrect genome ID in second parse.", "1005530.4", rep2.getGenomeId());
        RepGenome rep3;
        try {
            rep3 = new RepGenome("fig|12345.peg.4", "Invalid genome ID", "");
            fail("Invalid genome ID parsed correctly.");
        } catch (IllegalArgumentException e) {
            // Here the correct exception was thrown.
        }
        rep3 = new RepGenome("fig|1129793.4.peg.2957", "Glaciecola polaris LMG 21857", "MSHLAELVASAKAAISQASDVAALDNVRVEYLGKKGHLTLQMTTLRELPPEERPAAGAVI");
        int sim = rep3.similarity(rep1);
        int sim2 = ((ProteinKmers) rep3).similarity(rep1);
        assertEquals("Similarity depends on subclass used.", sim2, sim);
        // Test genome ordering.
        RepGenome rep4 = new RepGenome("fig|1129793.30.peg.2957", "Test genome 1", "");
        RepGenome rep5 = new RepGenome("fig|129793.30.peg.2957", "Test genome 2", "");
        assertTrue("Rep1 not less than rep2.", rep1.compareTo(rep2) < 0);
        assertTrue("Rep1 not less than rep3.", rep1.compareTo(rep3) < 0);
        assertTrue("Rep3 not less than rep4.", rep3.compareTo(rep4) < 0);
        assertTrue("Rep4 not less than rep5.", rep4.compareTo(rep5) < 0);
        assertTrue("Rep2 not greater than rep1.", rep2.compareTo(rep1) > 0);
        assertTrue("Rep3 not greater than rep1.", rep3.compareTo(rep1) > 0);
        assertTrue("Rep4 not greater than rep3.", rep4.compareTo(rep3) > 0);
        assertTrue("Rep5 not greater than rep4.", rep5.compareTo(rep4) > 0);
        rep3 = new RepGenome("fig|1005530.3.peg.2957", "Glaciecola polaris LMG 21857", "MSHLAELVASAKAAISQASDVAALDNVRVEYLGKKGHLTLQMTTLRELPPEERPAAGAVI");
        assertEquals("Equal genome IDs do not compare 0.", 0, rep1.compareTo(rep3));
    }

    /**
     * test RepGenomeDb
     *
     * @throws IOException
     */
    public void testRepGenomeDb() throws IOException {
        RepGenomeDb repDb = new RepGenomeDb(100);
        assertEquals("Wrong kmer size.", ProteinKmers.kmerSize(), repDb.getKmerSize());
        assertEquals("Wrong key protein.", "Phenylalanyl-tRNA synthetase alpha chain", repDb.getProtName());
        assertEquals("Wrong threshold.", 100, repDb.getThreshold());
        ProteinKmers.setKmerSize(9);
        repDb = new RepGenomeDb(200, "ATP synthase delta chain");
        assertEquals("Wrong kmer size in db2.", 9, repDb.getKmerSize());
        assertEquals("Wrong key protein in db2.", "ATP synthase delta chain", repDb.getProtName());
        assertEquals("Wrong threshold in db2.", 200, repDb.getThreshold());
        // Reset the kmer size.
        ProteinKmers.setKmerSize(10);
        // Process a fasta stream to create a rep-genome DB.
        File fastaFile = new File("src/test", "small.fa");
        FastaInputStream fastaStream = new FastaInputStream(fastaFile);
        repDb = new RepGenomeDb(50);
        repDb.addGenomes(fastaStream);
        fastaStream.close();
        // Find the representative of a genome.
        ProteinKmers testSeq = new ProteinKmers(
                "MSHLAELVASAKAAISQASDVAALDNVRVEYLGKKGHLTLQMTTLRELPPEERPAAGAVI" +
                "NEAKEQVQQALNARKAELESAALNARLAAETIDVSLPGRRIENGGLHPVTRTIDRIESFF" +
                "GELGFTVATGPEIEDDYHNFDALNIPGHHPARADHDTFWFDATRLLRTQTSGVQIRTMKA" +
                "QQPPIRIIAPGRVYRNDYDQTHTPMFHQMEGLIVDTNISFTNLKGTLHDFLRNFFEEDLQ" +
                "IRFRPSYFPFTEPSAEVDVMGKNGKWLEVLGCGMVHPNVLRNVGIDPEVYSGFAFGMGME" +
                "RLTMLRYGVTDLRSFFENDLRFLKQFK");
        RepGenomeDb.Representation result = repDb.findClosest(testSeq);
        assertEquals("E coli not found for E coli protein.", "1005530.3", result.getGenomeId());
        assertTrue("E coli not close enough to E coli protein.", result.getSimilarity() >= 200);
        // Now verify that all the sequences are represented.
        fastaStream = new FastaInputStream(fastaFile);
        for (Sequence inSeq : fastaStream) {
            boolean found = repDb.checkSimilarity(inSeq, 50);
            assertTrue("Genome " + inSeq.getLabel() + " not represented.", found);
        }
        fastaStream.close();
        // Now get all the represented genomes and verify that they are far apart.
        RepGenome[] allReps = repDb.all();
        int n = repDb.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                // Verify the similarity thresholds here.
                int compareij = allReps[i].similarity(allReps[j]);
                assertTrue("Genomes " + allReps[i] + " and " + allReps[j] + " are too close.  Score = " + compareij,
                        compareij < repDb.getThreshold());
                // Verify the distance behavior here.
                double distij = allReps[i].distance(allReps[j]);
                double distji = allReps[j].distance(allReps[i]);
                assertEquals("Distance not commutative.", distij, distji, 0.0);
                for (int k = j + 1; k < n; k++) {
                    double distjk = allReps[j].distance(allReps[k]);
                    double distik = allReps[i].distance(allReps[k]);
                    assertTrue("Triangle inequality failure.", distij + distjk >= distik);
                    int compareik = allReps[i].similarity(allReps[k]);
                    if (compareik > compareij && distik > distij) {
                        fail("Greater similarity at greater distance.");
                    } else if (compareik < compareij && distik < distij) {
                        fail("Lesser similarity at lesser distance.");
                    }
                }
            }
        }
        // Save this database.
        File saveFile = new File("src/test", "repdb.ser");
        repDb.save(saveFile);
        // Load it back in.
        ProteinKmers.setKmerSize(6);
        RepGenomeDb newDb = RepGenomeDb.load(saveFile);
        assertEquals("Incorrect protein name loaded.", repDb.getProtName(), newDb.getProtName());
        assertEquals("Incorrect kmer size loaded.", repDb.getKmerSize(), newDb.getKmerSize());
        assertEquals("Incorrect kmer set set.", newDb.getKmerSize(), ProteinKmers.kmerSize());
        assertEquals("Incorrect threshold loaded.", repDb.getThreshold(), newDb.getThreshold());
        assertEquals("Wrong number of genomes loaded.", repDb.size(), newDb.size());
        for (RepGenome oldGenome : allReps) {
            assertEquals("Genome " + oldGenome + " not loaded.", oldGenome, newDb.get(oldGenome.getGenomeId()));
        }

    }
}