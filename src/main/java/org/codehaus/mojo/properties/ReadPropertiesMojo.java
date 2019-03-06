package org.codehaus.mojo.properties;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.cli.CommandLineUtils;

/**
 * The read-project-properties goal reads property files and URLs and stores the properties as project properties. It
 * serves as an alternate to specifying properties in pom.xml. It is especially useful when making properties defined in
 * a runtime resource available at build time.
 *
 * @author <a href="mailto:zarars@gmail.com">Zarar Siddiqi</a>
 * @author <a href="mailto:Krystian.Nowak@gmail.com">Krystian Nowak</a>
 * @version $Id$
 */
@Mojo( name = "read-project-properties", defaultPhase = LifecyclePhase.NONE, requiresProject = true, threadSafe = true )
public class ReadPropertiesMojo
    extends AbstractMojo
{

    /**
     *
     */
    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    protected MavenSession session;

    /**
     * The properties files that will be used when reading properties.
     */
    @Parameter
    private File[] files = new File[0];

    /**
     * @param files The files to set for tests.
     */
    public void setFiles( File[] files )
    {
        if ( files == null )
        {
            this.files = new File[0];
        }
        else
        {
            this.files = new File[files.length];
            System.arraycopy( files, 0, this.files, 0, files.length );
        }
    }

    /**
     * The files that will be read as properties.
     */
    @Parameter
    private File[] readFiles = new File[0];

    /**
     * @param files The readFiles to set for tests.
     */
    public void setReadFiles( File[] readFiles )
    {
        if ( readFiles == null )
        {
            this.readFiles = new File[0];
        }
        else
        {
            this.readFiles = new File[readFiles.length];
            System.arraycopy( readFiles, 0, this.readFiles, 0, readFiles.length );
        }
    }

    /**
     * The URLs that will be used when reading properties. These may be non-standard URLs of the form
     * <code>classpath:com/company/resource.properties</code>. Note that the type is not <code>URL</code> for this
     * reason and therefore will be explicitly checked by this Mojo.
     */
    @Parameter
    private String[] urls = new String[0];

    /**
     * Default scope for test access.
     *
     * @param urls The URLs to set for tests.
     */
    public void setUrls( String[] urls )
    {
        if ( urls == null )
        {
            this.urls = null;
        }
        else
        {
            this.urls = new String[urls.length];
            System.arraycopy( urls, 0, this.urls, 0, urls.length );
        }
    }

    /**
     * If the plugin should be quiet if any of the files was not found
     */
    @Parameter( defaultValue = "false" )
    private boolean quiet;

    /**
     * Skip executing this plugin
     */
    @Parameter( defaultValue = "false" )
    private boolean skip;

    /**
     * Prefix that will be added before name of each property.
     * Can be useful for separating properties with same name from different files.
     */
    @Parameter
    private String keyPrefix = null;

    public void setKeyPrefix( String keyPrefix )
    {
        this.keyPrefix = keyPrefix;
    }

    /**
     * Used for resolving property placeholders.
     */
    private final PropertyResolver resolver = new PropertyResolver();

    /** {@inheritDoc} */
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( skip ) {
            getLog().info( "skipping" );
            return;
        }

        checkParameters();

        loadFiles();

        loadUrls();

        loadReadFiles();

        resolveProperties();
    }

    private void checkParameters()
        throws MojoExecutionException
    {
        if ( files.length > 0 && urls.length > 0 )
        {
            throw new MojoExecutionException( "Set files or URLs but not both - otherwise "
                + "no order of precedence can be guaranteed" );
        }
    }

    private void loadFiles()
        throws MojoExecutionException
    {
        for ( int i = 0; i < files.length; i++ )
        {
            load( new FileResource( files[i] ) );
        }
    }

    private void loadUrls()
        throws MojoExecutionException
    {
        for ( int i = 0; i < urls.length; i++ )
        {
            load( new UrlResource( urls[i] ) );
        }
    }

    private void loadReadFiles()
        throws MojoExecutionException
    {
        for ( int i = 0; i < readFiles.length; i++ )
        {
            loadAsProperty( new FileResource( readFiles[i] ) );
        }
    }

    private void load( Resource resource )
        throws MojoExecutionException
    {
        if ( resource.canBeOpened() )
        {
            loadProperties( resource );
        }
        else
        {
            missing( resource );
        }
    }

    private void loadAsProperty( FileResource resource )
        throws MojoExecutionException
    {
        if ( resource.canBeOpened() )
        {
            loadIntoProperty( resource );
        }
        else
        {
            missing( resource );
        }
    }

    private void loadProperties( Resource resource )
        throws MojoExecutionException
    {
        try
        {
            getLog().debug( "Loading properties from " + resource );

            final InputStream stream = resource.getInputStream();

            try
            {
                if ( keyPrefix != null )
                {
                    Properties properties = new Properties();
                    properties.load( stream );
                    Properties userProperties = session.getUserProperties();
                    for ( String key : properties.stringPropertyNames() )
                    {
                        userProperties.put( keyPrefix + key, properties.get( key ) );
                    }
                }
                else
                {
                    session.getUserProperties().load( stream );
                }
            }
            finally
            {
                stream.close();
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error reading properties from " + resource, e );
        }
    }

    private void loadIntoProperty( FileResource resource )
        throws MojoExecutionException
    {
        try
        {
            final String propertyName = keyPrefix != null ? keyPrefix + resource.getName() : resource.getName();

            getLog().debug( "Loading " + resource + " into property " + propertyName );

            session.getUserProperties().put( propertyName, new String( Files.readAllBytes( resource.toPath() ), StandardCharsets.UTF_8 ) );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error reading from " + resource, e );
        }
    }

    private void missing( Resource resource )
        throws MojoExecutionException
    {
        if ( quiet )
        {
            getLog().info( "Quiet processing - ignoring properties cannot be loaded from " + resource );
        }
        else
        {
            throw new MojoExecutionException( "Properties could not be loaded from " + resource );
        }
    }

    private void resolveProperties()
        throws MojoExecutionException, MojoFailureException
    {
        Properties environment = loadSystemEnvironmentPropertiesWhenDefined();
        Properties userProperties = session.getUserProperties();

        for ( Enumeration<?> n = userProperties.propertyNames(); n.hasMoreElements(); )
        {
            String k = (String) n.nextElement();
            userProperties.setProperty( k, getPropertyValue( k, userProperties, environment ) );
        }
    }

    private Properties loadSystemEnvironmentPropertiesWhenDefined()
        throws MojoExecutionException
    {
        Properties userProperties = session.getUserProperties();

        boolean useEnvVariables = false;
        for ( Enumeration<?> n = userProperties.propertyNames(); n.hasMoreElements(); )
        {
            String k = (String) n.nextElement();
            String p = (String) userProperties.get( k );
            if ( p.indexOf( "${env." ) != -1 )
            {
                useEnvVariables = true;
                break;
            }
        }
        Properties environment = null;
        if ( useEnvVariables )
        {
            try
            {
                environment = getSystemEnvVars();
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Error getting system environment variables: ", e );
            }
        }
        return environment;
    }

    private String getPropertyValue( String k, Properties p, Properties environment )
        throws MojoFailureException
    {
        try
        {
            return resolver.getPropertyValue( k, p, environment );
        }
        catch ( IllegalArgumentException e )
        {
            throw new MojoFailureException( e.getMessage() );
        }
    }

    /**
     * Override-able for test purposes.
     *
     * @return The shell environment variables, can be empty but never <code>null</code>.
     * @throws IOException If the environment variables could not be queried from the shell.
     */
    Properties getSystemEnvVars()
        throws IOException
    {
        return CommandLineUtils.getSystemEnvVars();
    }

    /**
     * Default scope for test access.
     *
     * @param quiet Set to <code>true</code> if missing files can be skipped.
     */
    void setQuiet( boolean quiet )
    {
        this.quiet = quiet;
    }

    /**
     * Default scope for test access.
     *
     * @param session The test session.
     */
    void setSession( MavenSession session )
    {
        this.session = session;
    }

    private static abstract class Resource
    {
        private InputStream stream;

        public abstract boolean canBeOpened();

        protected abstract InputStream openStream()
            throws IOException;

        public InputStream getInputStream()
            throws IOException
        {
            if ( stream == null )
            {
                stream = openStream();
            }
            return stream;
        }
    }

    private static class FileResource
        extends Resource
    {
        private final File file;

        public FileResource( File file )
        {
            this.file = file;
        }

        public boolean canBeOpened()
        {
            return file.exists();
        }

        protected InputStream openStream()
            throws IOException
        {
            return new BufferedInputStream( new FileInputStream( file ) );
        }

        public String toString()
        {
            return "File: " + file;
        }

        public String getName()
        {
            return file.getName();
        }

        public Path toPath()
        {
            return file.toPath();
        }
    }

    private static class UrlResource
        extends Resource
    {
        private static final String CLASSPATH_PREFIX = "classpath:";

        private static final String SLASH_PREFIX = "/";

        private final URL url;

        private boolean isMissingClasspathResouce = false;

        private String classpathUrl;

        public UrlResource( String url )
            throws MojoExecutionException
        {
            if ( url.startsWith( CLASSPATH_PREFIX ) )
            {
                String resource = url.substring( CLASSPATH_PREFIX.length(), url.length() );
                if ( resource.startsWith( SLASH_PREFIX ) )
                {
                    resource = resource.substring( 1, resource.length() );
                }
                this.url = getClass().getClassLoader().getResource( resource );
                if ( this.url == null )
                {
                    isMissingClasspathResouce = true;
                    classpathUrl = url;
                }
            }
            else
            {
                try
                {
                    this.url = new URL( url );
                }
                catch ( MalformedURLException e )
                {
                    throw new MojoExecutionException( "Badly formed URL " + url + " - " + e.getMessage() );
                }
            }
        }

        public boolean canBeOpened()
        {
            if ( isMissingClasspathResouce )
            {
                return false;
            }
            try
            {
                openStream();
            }
            catch ( IOException e )
            {
                return false;
            }
            return true;
        }

        protected InputStream openStream()
            throws IOException
        {
            return new BufferedInputStream( url.openStream() );
        }

        public String toString()
        {
            if ( !isMissingClasspathResouce )
            {
                return "URL " + url.toString();
            }
            return classpathUrl;
        }
    }
}
