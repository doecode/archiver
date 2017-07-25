/*
 */
package gov.osti.archiver;

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
public class GitLabTest {
    
    public GitLabTest() {
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
     * Test of importRepository method, of class GitLab.
     */
    @Test
    public void testImportRepository() throws Exception {
        Repository test = new Repository();
        test.setDescription("This is a testing import for chorus-reader from BitBucket.");
        test.setName("chorus-reader-bb");
        test.setRepositoryUrl("https://bitbucket.org/ensorn/chorus-reader");
        
        GitLab gitlab = new GitLab();
        gitlab.importRepository(test);
    }

    @Test
    public void testRepositoryJson() throws Exception {
        String json = "{\"id\":10,\"description\":\"This is a testing import for chorus-reader from BitBucket.\",\"default_branch\":null,\"tag_list\":[],\"public\":false,\"archived\":false,\"visibility_level\":0,\"ssh_url_to_repo\":\"git@lxdevrepo.osti.gov:osti_archive/chorus-reader-bb.git\",\"http_url_to_repo\":\"http://lxdevrepo.osti.gov/osti_archive/chorus-reader-bb.git\",\"web_url\":\"http://lxdevrepo.osti.gov/osti_archive/chorus-reader-bb\",\"owner\":{\"name\":\"OSTI Archiver\",\"username\":\"osti_archive\",\"id\":6,\"state\":\"active\",\"avatar_url\":\"http://www.gravatar.com/avatar/cdb301f8d4b318aeb00d6819694ab759?s=80&d=identicon\",\"web_url\":\"http://lxdevrepo.osti.gov/osti_archive\"},\"name\":\"chorus-reader-bb\",\"name_with_namespace\":\"OSTI Archiver / chorus-reader-bb\",\"path\":\"chorus-reader-bb\",\"path_with_namespace\":\"osti_archive/chorus-reader-bb\",\"container_registry_enabled\":true,\"issues_enabled\":true,\"merge_requests_enabled\":true,\"wiki_enabled\":true,\"builds_enabled\":true,\"snippets_enabled\":false,\"created_at\":\"2017-05-16T19:34:24.153Z\",\"last_activity_at\":\"2017-05-16T19:34:24.153Z\",\"shared_runners_enabled\":true,\"lfs_enabled\":true,\"creator_id\":6,\"namespace\":{\"id\":6,\"name\":\"osti_archive\",\"path\":\"osti_archive\",\"owner_id\":6,\"created_at\":\"2017-05-15T18:18:05.841Z\",\"updated_at\":\"2017-05-15T18:18:05.841Z\",\"description\":\"\",\"avatar\":null,\"share_with_group_lock\":false,\"visibility_level\":20,\"request_access_enabled\":false,\"deleted_at\":null,\"lfs_enabled\":null,\"parent_id\":null},\"avatar_url\":null,\"star_count\":0,\"forks_count\":0,\"open_issues_count\":0,\"runners_token\":\"8QKaQh51gRNBo4fS3fCg\",\"public_builds\":true,\"shared_with_groups\":[],\"only_allow_merge_if_build_succeeds\":false,\"request_access_enabled\":false,\"only_allow_merge_if_all_discussions_are_resolved\":false}";
        
        Repository repo = Repository.fromJson(json);
        
        assertEquals("ID is wrong", new Integer(10), repo.getId());
        assertEquals("description is wrong", "This is a testing import for chorus-reader from BitBucket.", repo.getDescription());
        assertEquals("Owner name is wrong", "OSTI Archiver", repo.getOwner().getName());
    }
    
    /**
     * Test of getRepository method, of class GitLab.
     */
    @Test
    public void testGetRepository() throws Exception {
        System.out.println("GET test: ");
        
        Repository repo = GitLab.getRepository(7);
        
        System.out.println("Response: " + repo.toJson().asText());
        
    }
    
}
