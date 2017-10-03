/*
 */
package gov.osti.archiver;

import gov.osti.archiver.entity.Project;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author ensornl
 */
public class ArchiverTest {
    
    public ArchiverTest() {
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
     * Test of getFolderFor method, of class Archiver.
     */
    @Test
    public void testGetFolderFor() {
        Project p = new Project();
        
        assertEquals("Should be empty", "", Archiver.getFolderFor(p));
        
        p.setFileName("/some/file/system/test.sh");
        
        assertEquals("Wrong filename", "test.sh", Archiver.getFolderFor(p));
        
        p.setFileName(null);
        p.setRepositoryLink("https://github.com/myproject/things");
        
        assertEquals("Wrong url", "things", Archiver.getFolderFor(p));
        
        p.setRepositoryLink("https://github.com/endswith/aslash/");
        
        assertEquals("Wrong url", "aslash", Archiver.getFolderFor(p));
        
        p.setRepositoryLink("");
        
        assertEquals("Should be empty", "", Archiver.getFolderFor(p));
        
        p.setRepositoryLink("///////");
        
        assertEquals("Should be empty", "", Archiver.getFolderFor(p));
    }
    
}
