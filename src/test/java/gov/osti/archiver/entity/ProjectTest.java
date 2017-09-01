/*
 */
package gov.osti.archiver.entity;

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
public class ProjectTest {
    
    public ProjectTest() {
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
     * Ensure the PROJECT NAME set method is removing illegal characters.
     */
    @Test
    public void testProjectNameFilter() {
        Project project = new Project();
        
        assertNull  ("Empty should be null", project.getProjectName());
        
        project.setProjectName("simple");
        assertEquals("name is wrong", "simple", project.getProjectName());
        
        project.setProjectName("Comp/lic8t._me @*(#@$");
        assertEquals("name is wrong", "Complic8t._me ", project.getProjectName());
        
        // add a CODEID value to check for suffix addition
        project.setCodeId(2359l);
        project.setProjectName("Just$2test");
        
        assertEquals("name with codeID wrong", "Just2test_2359", project.getProjectName());
    }

    /**
     * Ensure the PROJECT DESCRIPTION truncates properly.
     */
    @Test
    public void testProjectDescriptionTruncation() {
        Project project = new Project();
        
        assertNull   ("empty should be null", project.getProjectDescription());
        
        project.setProjectDescription("Something short.");
        assertEquals ("Value is wrong", "Something short.", project.getProjectDescription());
        
        String description = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer dui risus, molestie sed accumsan id, fermentum vitae leo. Donec pretium dui massa, sed finibus enim dignissim a. Quisque iaculis lectus lacus, in fermentum dui iaculis eu. Aenean sodales porttitor pellentesque. In egestas nisi nec nisi aliquam, sit amet pharetra leo posuere. Aenean cursus mauris nisl, sit amet tristique tellus rhoncus ac. Donec diam enim, accumsan sit amet quam vitae, efficitur suscipit purus. Aenean ac metus arcu. Phasellus mauris augue, ornare a turpis eget, facilisis volutpat est. Phasellus tincidunt arcu tortor. Ut aliquam ac leo et aliquam. Sed tempus tellus eu tortor tristique congue. Phasellus pulvinar, mi non elementum mattis, elit turpis tincidunt dolor, ac cursus quam nulla in risus. Donec quis lectus quis ex convallis fringilla et ac metus. Donec hendrerit risus aliquam congue finibus. Ut ut pharetra nunc. Nullam gravida purus leo, eu mollis urna consectetur ut. Morbi id molestie tellus. Etiam eu ipsum quis tortor tristique dignissim. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Praesent vel orci sed nunc commodo dapibus. Vestibulum nec arcu pretium, finibus nisl nec, feugiat nulla. Morbi euismod neque vel libero vestibulum, nec tincidunt ante pellentesque. Curabitur pulvinar malesuada massa, vel elementum tellus faucibus at. Aenean sed arcu at magna fermentum rutrum eu tincidunt ligula. Nam sit amet felis tempus, maximus justo nec, interdum diam. Phasellus tempor elit sed massa vehicula, quis malesuada felis iaculis. Vestibulum eget viverra felis. Sed libero justo, scelerisque placerat mi a, posuere gravida quam. Donec ac blandit libero, quis vestibulum lacus. Vestibulum in bibendum ligula. Curabitur ullamcorper odio vestibulum ex ullamcorper accumsan. Praesent risus urna, ultrices nec nibh molestie, finibus elementum mauris. Integer feugiat a magna nec bibendum. Sed aliquet consequat lacinia. Donec ante augue, fermentum at ipsum eu cras amet.";
        project.setProjectDescription(description);
        
        assertEquals ("too long?", 2000, project.getProjectDescription().length());
        assertEquals ("incorrect?", description.substring(0,2000), project.getProjectDescription());
    }
    
}
