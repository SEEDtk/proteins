package org.theseed.proteins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import org.theseed.proteins.kmers.ProteinKmers;
import org.theseed.sequence.FastaInputStream;
import org.theseed.sequence.FastaOutputStream;
import org.theseed.sequence.Sequence;
import org.theseed.utils.MagicMap;

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
     * Test the Role object
     */
    public void testRole() {
        // We need to verify that the normalization works.  We create
        // equivalent roles and insure their checksums are equal.
        String rDesc = "(R)-2-hydroxyacid dehydrogenase, similar to L-sulfolactate dehydrogenase (EC 1.1.1.272)";
        Role rObj1 = new Role("2HydrDehySimiLSulf", rDesc);
        assertEquals("Role ID not stored properly.", "2HydrDehySimiLSulf", rObj1.getId());
        Role rObj2 = new Role("2HydrDehySimiLSulf2", "(R)-2-hydroxyacid dehydrogenase, similar to L-sulfolactate dehydrogenase");
        assertEquals("EC number affects checksum.", rObj1.getChecksum(), rObj2.getChecksum());
        assertEquals("Equal checksums is not equal roles.", rObj1, rObj2);
        assertEquals("Equal checksums does not compare-0 roles.", 0, rObj1.compareTo(rObj2));
        assertEquals("Equal roles have different hashcodes.", rObj1.hashCode(), rObj2.hashCode());
        Role rObj3 = new Role("2HydrDehySimiLSulf3", "(r)-2-hydroxyacid dehydrogenase, similar to L-sulfolactate dehydrogenase");
        assertEquals("Role checksums are case sensitive.", rObj1, rObj3);
        Role rObj4 = new Role("2HydrDehySimiLSulf4", "(R)-2-hydroxyacid dehydrogenase (EC 1.1.1.272), similar to L-sulfolactate dehydrogenase");
        assertEquals("Role checksums affected by EC position.", rObj1, rObj4);
        Role rObj5 = new Role("2HydrDehySimiLSulf5", "(R)-2-hydroxyacid dehydrogenase similar to L-sulfolactate dehydrogenase");
        assertEquals("Role checksum affected by comma.", rObj1, rObj5);
    }

    /**
     * Test magic IDs.
     * @throws IOException
     */
    public void testMagic() throws IOException {
        File inFile = new File("src/test", "words.txt");
        Scanner roleScanner = new Scanner(inFile);
        roleScanner.useDelimiter("\t|\r\n|\n");
        while (roleScanner.hasNext()) {
            String condensed = roleScanner.next();
            String full = roleScanner.next();
            assertEquals("String did not condense.", condensed, MagicMap.condense(full));
        }
        roleScanner.close();
        // Test registration
        RoleMap magicTable = new RoleMap();
        inFile = new File("src/test", "roles.txt");
        roleScanner = new Scanner(inFile);
        roleScanner.useDelimiter("\t|\r\n|\n");
        while (roleScanner.hasNext()) {
            String roleId = roleScanner.next();
            roleScanner.next();
            assertNull("Wrong ID found", magicTable.get(roleId));
            String roleDesc = roleScanner.next();
            Role newRole = new Role(roleId, roleDesc);
            magicTable.register(newRole);
            assertEquals("Registered ID did not read back.", roleDesc, magicTable.getName(roleId));
        }
        roleScanner.close();
        assertTrue("PheS not found.", magicTable.containsKey("PhenTrnaSyntAlph"));
        assertFalse("Known bad key found.", magicTable.containsKey("PhenTrnaSyntGamm"));
        String modifiedRole = "3-keto-L-gulonate-6-phosphate decarboxylase UlaK putative (L-ascorbate utilization protein D) (EC 4.1.1.85)";
        Role newRole = magicTable.findOrInsert(modifiedRole);
        assertEquals("Wrong ID assigned for modified role.", "3KetoLGulo6PhosDeca6", newRole.getId());
        assertSame("Modified role did not read back.", newRole, magicTable.get("3KetoLGulo6PhosDeca6"));
        modifiedRole = "Unique (new) role string without numbers";
        newRole = magicTable.findOrInsert(modifiedRole);
        assertEquals("Wrong ID assigned for unique role.", "UniqRoleStriWith", newRole.getId());
        assertSame("Unique role did not read back.", newRole, magicTable.get("UniqRoleStriWith"));
        Role findRole = magicTable.findOrInsert(modifiedRole);
        assertSame("Unique role was re-inserted.", newRole, findRole);
        modifiedRole = "Unique (old) role string without numbers";
        findRole = magicTable.findOrInsert(modifiedRole);
        assertTrue("Parenthetical did not change role ID.", findRole != newRole);
        assertEquals("Wrong ID assigned for parenthetical role.", "UniqRoleStriWith2", findRole.getId());
        assertSame("Parenthetical role did not read back.", findRole, magicTable.get("UniqRoleStriWith2"));
        modifiedRole = "Unique (newer) role string without numbers";
        newRole = magicTable.findOrInsert(modifiedRole);
        assertEquals("Wrong ID assigned for newer role.", "UniqRoleStriWith3", newRole.getId());
        assertSame("Parenthetical role did not read back.", newRole, magicTable.get("UniqRoleStriWith3"));
        modifiedRole = "Unique role string 12345 with numbers";
        newRole = magicTable.findOrInsert(modifiedRole);
        assertEquals("Name not stored in role.", modifiedRole, newRole.getName());
        assertEquals("Wrong ID assigned for numbered role.", "UniqRoleStri1234n1", newRole.getId());
        modifiedRole = "Unique role string 12345 with more numbers";
        newRole = magicTable.findOrInsert(modifiedRole);
        assertEquals("Wrong ID assigned for second numbered role.", "UniqRoleStri1234n2", newRole.getId());
        // Test save and load.
        File saveFile = new File("src/test", "roles.ser");
        magicTable.save(saveFile);
        RoleMap newTable = RoleMap.load(saveFile);
        for (Role oldRole : magicTable.values()) {
            newRole = newTable.get(oldRole.getId());
            assertNotNull("Could not find role in loaded table.", newRole);
            assertEquals("Loaded table has wrong role name.", newRole.getName(), oldRole.getName());
            assertEquals("Loaded role has wrong checksum.", newRole, oldRole);
        }
    }

    /**
     * test protein kmers
     */
    public void testKmers() {
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
        assertEquals("Kmer1 too close to kmer2.", 0.0, kmer1.distance(kmer2), 0.0);
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


}
