package org.codehaus.mojo.properties;

import org.apache.maven.execution.MavenSession;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class ReadPropertiesMojoTest {
    private static final String NEW_LINE = System.getProperty("line.separator");

    private MavenSession sessionStub;
    private ReadPropertiesMojo readPropertiesMojo;

    @SuppressWarnings( "deprecation" )
	@Before
    public void setUp() {
        sessionStub = new MavenSession(null,null,null,null,null,null,null,null,new Properties(),null);
        readPropertiesMojo = new ReadPropertiesMojo();
        readPropertiesMojo.setSession(sessionStub);
    }


    @Test
    public void readPropertiesWithoutKeyprefix() throws Exception {
        File testPropertyFile = getPropertyFileForTesting();
        // load properties directly for comparison later
        Properties testProperties = new Properties();
        testProperties.load(new FileReader(testPropertyFile));

        // do the work
        readPropertiesMojo.setFiles(new File[]{testPropertyFile});
        readPropertiesMojo.execute();

        // check results
        Properties userProperties = sessionStub.getUserProperties();
        assertNotNull(userProperties);
        // it should not be empty
        assertNotEquals(0, userProperties.size());

        // we are not adding prefix, so properties should be same as in file
        assertEquals(testProperties.size(), userProperties.size());
        assertEquals(testProperties, userProperties);

    }

    @Test
    public void readPropertiesWithKeyprefix() throws Exception {
        String keyPrefix = "testkey-prefix.";

        File testPropertyFileWithoutPrefix = getPropertyFileForTesting();
        Properties testPropertiesWithoutPrefix = new Properties();
        testPropertiesWithoutPrefix.load(new FileReader(testPropertyFileWithoutPrefix));
        // do the work
        readPropertiesMojo.setKeyPrefix(keyPrefix);
        readPropertiesMojo.setFiles(new File[]{testPropertyFileWithoutPrefix});
        readPropertiesMojo.execute();

        // load properties directly and add prefix for comparison later
        Properties testPropertiesPrefix = new Properties();
        testPropertiesPrefix.load(new FileReader(getPropertyFileForTesting(keyPrefix)));

        // check results
        Properties userProperties = sessionStub.getUserProperties();
        assertNotNull(userProperties);
        // it should not be empty
        assertNotEquals(0, userProperties.size());

        // we are adding prefix, so prefix properties should be same as in projectProperties
        assertEquals(testPropertiesPrefix.size(), userProperties.size());
        assertEquals(testPropertiesPrefix, userProperties);

        // properties with and without prefix shouldn't be same
        assertNotEquals(testPropertiesPrefix, testPropertiesWithoutPrefix);
        assertNotEquals(testPropertiesWithoutPrefix, userProperties);

    }

    private File getPropertyFileForTesting() throws IOException {
        return getPropertyFileForTesting(null);
    }

    private File getPropertyFileForTesting(String keyPrefix) throws IOException {
        File f = File.createTempFile("prop-test", ".properties");
        f.deleteOnExit();
        FileWriter writer = new FileWriter(f);
        String prefix = keyPrefix;
        if (prefix == null) {
            prefix = "";
        }
        try {
            writer.write(prefix + "test.property1=value1" + NEW_LINE);
            writer.write(prefix + "test.property2=value2" + NEW_LINE);
            writer.write(prefix + "test.property3=value3" + NEW_LINE);
            writer.flush();
        } finally {
            writer.close();
        }
        return f;
    }

}
