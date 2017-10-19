/*
 */
package gov.osti.archiver.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import gov.osti.archiver.entity.ArchiveRequest;
import gov.osti.archiver.entity.Project;
import gov.osti.archiver.listener.ServletContextListener;
import gov.osti.archiver.util.Extractor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.compress.archivers.ArchiveException;
import org.eclipse.jgit.util.StringUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Archiver web services. 
 * 
 * GET /project/{codeId} -- retrieve existing Project if possible
 * POST /project -- store and archive a new Project
 * 
 * @author ensornl
 */
@Path("project")
public class ArchiveResource {
    // logger
    private static Logger log = LoggerFactory.getLogger(ArchiveResource.class);
    // base filesystem path to save information into
    private static String FILE_BASEDIR = ServletContextListener.getConfigurationProperty("file.archive");

    // XML/JSON mapper reference
    private static final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    
    /**
     * Creates a new instance of ArchiveResource
     */
    public ArchiveResource() {
    }
    
    /**
     * Attempt to look up a given Project by its CODE ID.
     * 
     * Response Code:
     * 200 - record found, returns JSON
     * 404 - record not on file
     * 
     * @param projectId the CODE ID to look for
     * @return JSON of the Project if found
     */
    @GET
    @Path ("{projectId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response find(@PathParam ("projectId") Long projectId) {
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            Project project = em.find(Project.class, projectId);
            
            // not found? say so.
            if (null==project)
                return ErrorResponse
                        .notFound("Indicated Project not on file.")
                        .build();
            
            // found it, return as JSON
            return Response
                    .status(Response.Status.OK)
                    .entity(project.toJson())
                    .build();
        } finally {
            em.close();
        }
    }
    
    /**
     * Query for any PROJECT mapping to the indicated CODE ID from DOECODE.
     * 
     * Returns:
     * 200 - JSON containing referencing project(s)
     * 404 - No project found mapping to the indiciated CODE ID
     * 500 - JSON or database processing error
     * 
     * @param codeId the CODE ID value to search for
     * @return a Response containing the information
     */
    @GET
    @Path ("/codeid/{codeId}")
    @Produces (MediaType.APPLICATION_JSON)
    public Response findByCodeId(@PathParam ("codeId") Long codeId) {
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            // create a Set containing the CODE ID
            Set<Long> ids = new HashSet<>();
            ids.add(codeId);
            // Query it up
            TypedQuery<Project> query = em.createNamedQuery("Project.findByCodeId", Project.class)
                    .setParameter("ids", ids);
            List<Project> results = query.getResultList();
            
            // if no records match, return a Not Found response; otherwise, JSON
            return ( 0==results.size() ) ?
                    ErrorResponse
                    .notFound("Code ID not found.")
                    .build() :
                    Response
                    .ok()
                    .entity(mapper.writeValueAsString(results))
                    .build();
        } catch ( IOException e ) {
            log.warn("JSON parser error", e);
            return ErrorResponse
                    .internalServerError("JSON mapping error")
                    .build();
        } finally {
            em.close();
        }
    }
    
    /**
     * Obtain a listing of any Projects in a given status condition.
     * 
     * @param status the Status code to use ("Error", "Pending", or "Complete")
     * @return JSON containing an array of Project records in status, if any.
     */
    @GET
    @Path ("/status/{status}")
    @Produces (MediaType.APPLICATION_JSON)
    public Response findByStatus(@PathParam("status") String status) {
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            TypedQuery<Project> query = em.createNamedQuery("Project.findByStatus", Project.class)
                    .setParameter("status", Project.Status.valueOf(status));
            List<Project> results = query.getResultList();
            
            return Response
                    .ok()
                    .entity(mapper.writeValueAsString(results))
                    .build();
        } catch ( IllegalArgumentException e ) {
            return ErrorResponse
                    .badRequest("Unknown status type " + status)
                    .build();
        } catch ( IOException e ) {
            log.warn("JSON Error", e);
            return ErrorResponse
                    .internalServerError("JSON processing error.")
                    .build();
        } finally {
            em.close();
        }
    }
    
    /**
     * Perform ARCHIVING of a given PROJECT.
     * 
     * Passed-in JSON should contain a CODE_ID from DOECODE, a PROJECT NAME,
     * PROJECT DESCRIPTION, and one of REPOSITORY LINK or FILE NAME.  If the former,
     * directly import into the archive; if the latter, extra steps need to be
     * taken to extract the file (assumed to be a compressed archive) into a holding
     * area, a git repository made of its content.
     * 
     * Procedure:
     * 1. look up the REPOSITORY LINK value if supplied.
     * 2. if found, add this CODE ID to its mapping, done.
     * 3. if not, create a PROJECT and attempt to git-import the content to cache. (separate thread)
     * 
     * File uploads are considered to be new entities each time, so a new PROJECT 
     * is always created.
     * 
     * Response Codes:
     * 200 - Project already archived (no changes to repository or file), returns JSON
     * 201 - Created a new PROJECT and called the background thread to import
     * 400 - Missing required field(s) for processing, or unrecognized archive file format
     * 500 - unable to read the JSON
     * 
     * @param json the JSON of the PROJECT to archive
     * @param file (optional) a file, assumed to be a compressed archive, containing
     * the source code of the project
     * @param fileInfo (optional) if present, the filename disposition of the file
     * @return 
     */
    private Response doArchive(String json, InputStream file, FormDataContentDisposition fileInfo) {
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            ArchiveRequest ar = mapper.readValue(json, ArchiveRequest.class);
            
            // must have a CODE_ID value
            if (null==ar.getCodeId())
                return ErrorResponse
                        .badRequest("Missing required Code ID value.")
                        .build();
            
            //  construct a new PROJECT
            Project project = new Project();
            project.setRepositoryLink(ar.getRepositoryLink());
            project.addCodeId(ar.getCodeId());
            
            em.getTransaction().begin();
            
            // do we have a REPOSITORY LINK?
            if (!StringUtils.isEmptyOrNull(ar.getRepositoryLink())) {
                // FORCE protocol if not present
                if (!ar.getRepositoryLink().startsWith("http"))
                    ar.setRepositoryLink("https://" + ar.getRepositoryLink());
                // see if it's ALREADY been cached
                TypedQuery<Project> query = em.createNamedQuery("Project.findByRepositoryLink", Project.class)
                        .setParameter("url", ar.getRepositoryLink());
                
                try {
                    Project p = query.getSingleResult();
                    
                    // may need to add this CODE ID if multiple projects post to this
                    if ( p.addCodeId(ar.getCodeId()) ) {
                        // added one, merge it in
                        em.merge(p);
                        em.getTransaction().commit();
                    }
                    
                    // found it, send it back
                    return Response
                            .ok()
                            .entity(p.toJson())
                            .build();
                } catch ( NoResultException e ) {
                    // this is expected if not on file already, proceed
                }
                // no such thing, go ahead and create a PROJECT to hold this
                project.setStatus(Project.Status.Pending);
                
                em.persist(project); // get the UUID
                
            } else if (null!=file) {
                // we have a FILE to do; create a PROJECT and store it
                em.persist(project); // get us a PROJECT ID
                
                // attempt to store and extract the archive file
                try {
                    String fileName = saveFile(file, project.getProjectId(), fileInfo.getFileName());
                    project.setFileName(fileName);
                    // ensure we can tell what sort of archive we have
                    if (null==Extractor.detectArchiveFormat(fileName))
                        throw new ArchiveException("Invalid or unknown archive format.");
                } catch ( ArchiveException e ) {
                    log.warn("Invalid Archive for " + fileInfo.getFileName() + ": " + e.getMessage());
                    return ErrorResponse
                            .badRequest("Unrecognized archive file type, unsupported format.")
                            .build();
                } catch ( IOException e ) {
                    log.error ("File Upload Failed: " + e.getMessage());
                    return ErrorResponse
                            .internalServerError("File upload operation failed.")
                            .build();
                }
            } else {
                return ErrorResponse
                        .badRequest("Missing required parameters.")
                        .build();
            }
            // got this far, we must be ready to call the background thread
            em.getTransaction().commit();

            // fire off the background thread
            ServletContextListener.callArchiver(project);

            // return 201 with JSON
            return Response
                    .status(Response.Status.CREATED)
                    .entity(project.toJson())
                    .build();
        } catch ( IOException e ) { 
            log.warn("JSON Parser Error: " + e.getMessage());
            return ErrorResponse
                    .internalServerError("JSON parsing error.")
                    .build();
        } catch ( PersistenceException e ) {
            log.warn("Database Error: ",e);
            return ErrorResponse
                    .internalServerError("Database persistence error.")
                    .build();
        } finally {
            if (em.getTransaction().isActive())
                em.getTransaction().rollback();
            em.close();
        }
    }
    
    @GET
    @Produces (MediaType.APPLICATION_JSON)
    @Path ("/update/{projectId}")
    public Response update(@PathParam("projectId") Long projectId) {
        EntityManager em = ServletContextListener.createEntityManager();
        
        try {
            Project p = em.find(Project.class, projectId);
            
            if (null==p) {
                return ErrorResponse
                        .notFound("Project not on file.")
                        .build();
            }
            ServletContextListener.callSync(p);
            
            return Response
                    .ok()
                    .entity(mapper.createObjectNode().put("project", projectId).put("status", "OK").toString())
                    .build();
        } finally {
            em.close();
        }
    }
    
    /**
     * POST a Project to be archived.  
     * 
     * Response Code:
     * 201 -- created new Project, OK
     * 400 -- no CODE ID supplied
     * 500 -- unable to parse incoming JSON
     * 
     * @param json the JSON of the Project to access
     * @param file (for multi-part upload requests) archive file containing
     * @param fileInfo (multi-part uploads) file disposition information
     * source project
     * @return 
     */
    @POST
    @Consumes (MediaType.MULTIPART_FORM_DATA)
    @Produces (MediaType.APPLICATION_JSON)
    public Response archive(
            @FormDataParam("project") String json,
            @FormDataParam("file") InputStream file,
            @FormDataParam("file") FormDataContentDisposition fileInfo) {
        // call the ARCHIVE process to do the work
        return doArchive(json, file, fileInfo);
    }
    
    /**
     * POST a Project to archive in JSON format.  JSON should contain at least
     * a CODE_ID and PROJECT_NAME value, with a REPOSITORY_LINK.
     * 
     * Response Codes:
     * 200 - OK, project is already on file
     * 201 - CREATED, new project created and logged
     * 400 - BAD REQUEST, missing required CODE_ID to map Project to
     * 500 - INTERNAL SERVER ERROR, unable to process JSON request
     * 
     * @param json the JSON of the Project to archive
     * @return a Response according to the disposition of the archived Project
     */
    @POST
    @Consumes (MediaType.APPLICATION_JSON)
    @Produces (MediaType.APPLICATION_JSON)
    public Response archive(String json) {
        return doArchive(json, null, null);
    }
    
    /**
     * Store a given File InputStream to a new base absolute path.
     * @param in the InputStream containing the File
     * @param projectId the CODE ID of the DOECODE Project to associate with
     * @param fileName the base file name to use
     * @throws IOException on IO errors
     * @return the new File name complete path
     */
    private static String saveFile(InputStream in, Long projectId, String fileName) throws IOException {
        // store this file in a designated base path
        java.nio.file.Path destination = Paths.get(FILE_BASEDIR, String.valueOf(projectId), fileName);
        // make the necessary file paths
        Files.createDirectories(destination.getParent());
        // save it
        Files.copy(in, destination);
        
        return destination.toString();
    }
    
    /**
     * Delete a PROJECT'S cache files, including any extracted files, if found.
     * 
     * Should be used as a RESET BUTTON for this Project.  Will do nothing if
     * no files exist to delete.
     * 
     * @param projectId the PROJECT ID to wipe out
     * @throws IOException on file IO errors
     */
    private static void wipeFiles(Long projectId) throws IOException {
        // only do this if FILES EXIST
        if ( !Files.exists(Paths.get(FILE_BASEDIR, String.valueOf(projectId))) )
                return;
        
        // starting at the FILE_BASEDIR + codeId, wipe out the cached files and
        // any folders
        Files.walkFileTree(Paths.get(FILE_BASEDIR, String.valueOf(projectId)), 
                new SimpleFileVisitor<java.nio.file.Path>() {
            @Override
            public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attrs)
            throws IOException
            {
                // delete this file if present
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(java.nio.file.Path directory, IOException e) throws IOException {
                // if there's no Exception, delete this, otherwise throw it
                if (null==e) {
                    // wipe this directory; should be empty
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                } else {
                    // cannot follow directory, abort
                    throw e;
                }
            }
        });
    }
}
