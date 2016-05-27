package org.bonitasoft.platform.configuration.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * @author Laurent Leseigneur
 */
public class FullBonitaConfigurationTest {

    @Test
    public void should_have_readable_toString() throws Exception {
        //given
        FullBonitaConfiguration fullBonitaConfiguration = new FullBonitaConfiguration("resourceName", "content".getBytes(), "type", 147L);

        //then
        assertThat(fullBonitaConfiguration.toString()).isEqualTo("FullBonitaConfiguration{ resourceName='resourceName' , configurationType='type' , tenantId=147 }");
    }

}
