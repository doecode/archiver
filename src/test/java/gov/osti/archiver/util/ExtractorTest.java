/*
 */
package gov.osti.archiver.util;

import gov.osti.archiver.listener.ServletContextListener;
import java.nio.file.Paths;
import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test various Extractor-related utility methods.
 * 
 * @author ensornl
 */
public class ExtractorTest {
    // logger
    private static Logger log = LoggerFactory.getLogger(ExtractorTest.class);
    // base file folder containing archive uploads
    private static String FILE_BASEDIR = ServletContextListener.getConfigurationProperty("file.basedir");
    // base folder for relative test files
    private static String BASEDIR = System.getProperty("basedir");
    
    public ExtractorTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }
    
    /**
     * Acquire the local path for a testing file resource.
     * 
     * @param basename the base file name requested
     * @return the absolute system path to the file
     */
    private static String getTestFileFor(String basename) {
        return Paths.get(BASEDIR, "test_files", basename).toString();
    }
    
    /**
     * Ensure that common archive and compressed archive formats work as advertised,
     * and can be identified properly.
     * 
     * @throws Exception on unexpected errors
     */
    @Test
    public void testIdentifyArchive() throws Exception {
        String[] files = { "test.zip", "test.tgz", "test.tar.gz", "test.jar", "test.war", "test.tar" };
        
        // we expect all these to pass
        for ( String file : files ) {
            assertNotNull ("Cannot identify file: " + file, Extractor.detectArchiveFormat(getTestFileFor(file)));
        }
        try {
            // we expect this one to fail
            assertNull  ("Identified base text file?", Extractor.detectArchiveFormat(getTestFileFor("text_file.txt")));
            
            fail ("Text file passed extraction detection.");
        } catch ( ArchiveException e ) {
            // this was expected
        }
    }
}
