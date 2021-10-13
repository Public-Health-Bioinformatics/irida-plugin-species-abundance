package org.publichealthbioinformatics.irida.plugin.speciesabundance;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.junit.*;
import org.mockito.ArgumentCaptor;
import org.hamcrest.collection.IsMapContaining;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import ca.corefacility.bioinformatics.irida.exceptions.IridaWorkflowException;
import ca.corefacility.bioinformatics.irida.model.sample.MetadataTemplateField;
import ca.corefacility.bioinformatics.irida.model.sample.metadata.MetadataEntry;
import ca.corefacility.bioinformatics.irida.model.sequenceFile.SingleEndSequenceFile;
import ca.corefacility.bioinformatics.irida.model.workflow.description.*;
import ca.corefacility.bioinformatics.irida.model.sample.Sample;
import ca.corefacility.bioinformatics.irida.model.workflow.IridaWorkflow;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.AnalysisOutputFile;
import ca.corefacility.bioinformatics.irida.model.workflow.submission.AnalysisSubmission;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.Analysis;
import ca.corefacility.bioinformatics.irida.service.sample.SampleService;
import ca.corefacility.bioinformatics.irida.service.sample.MetadataTemplateService;
import ca.corefacility.bioinformatics.irida.service.workflow.IridaWorkflowsService;


public class SpeciesAbundancePluginUpdaterTest {
    private String WORKFLOW_NAME = "species-abundance";
    private String WORKFLOW_VERSION = "0.1.0";

    private SpeciesAbundancePluginUpdater updater;

    private SampleService sampleService;
    private MetadataTemplateService metadataTemplateService;
    private IridaWorkflowsService iridaWorkflowsService;
    private IridaWorkflow iridaWorkflow;
    private IridaWorkflowDescription iridaWorkflowDescription;

    private UUID uuid = UUID.randomUUID();

    @Before
    public void setUp() throws IridaWorkflowException {
        sampleService = mock(SampleService.class);
        metadataTemplateService = mock(MetadataTemplateService.class);
        iridaWorkflowsService = mock(IridaWorkflowsService.class);
        iridaWorkflow = mock(IridaWorkflow.class);
        iridaWorkflowDescription = mock(IridaWorkflowDescription.class);

        updater = new SpeciesAbundancePluginUpdater(metadataTemplateService, sampleService, iridaWorkflowsService);

        when(iridaWorkflowsService.getIridaWorkflow(uuid)).thenReturn(iridaWorkflow);
        when(iridaWorkflow.getWorkflowDescription()).thenReturn(iridaWorkflowDescription);
        when(iridaWorkflowDescription.getName()).thenReturn(WORKFLOW_NAME);
        when(iridaWorkflowDescription.getVersion()).thenReturn(WORKFLOW_VERSION);
    }

    @Test
    public void testUpdate() throws Throwable {
        ImmutableMap<String, String> expectedResults = ImmutableMap.<String, String>builder()
                .put("species-abundance/taxon_name", "Escherichia coli")
                .put("species-abundance/taxonomy_level", "S")
                .put("species-abundance/taxonomy_id", "562")
                .put("species-abundance/proportion", "0.98546")
                .build();
        Path speciesAbundanceFilePath = Paths.get(ClassLoader.getSystemResource("species_abundance.tsv").toURI());

        AnalysisOutputFile speciesAbundanceFile = new AnalysisOutputFile(speciesAbundanceFilePath, null, null, null);
        Analysis analysis = new Analysis(null, ImmutableMap.of("species_abundance", speciesAbundanceFile), null, null);
        AnalysisSubmission submission = AnalysisSubmission.builder(uuid)
                .inputFiles(ImmutableSet.of(new SingleEndSequenceFile(null))).build();

        submission.setAnalysis(analysis);

        Sample sample = new Sample();
        sample.setId(0L);

        MetadataEntry metadataEntry = new MetadataEntry("", "", new MetadataTemplateField("", ""));

        Set<MetadataEntry> metadataEntries = new HashSet<>();
        metadataEntries.add(metadataEntry);

        when(metadataTemplateService.convertMetadataStringsToSet(any(Map.class))).thenReturn(metadataEntries);

        updater.update(Lists.newArrayList(sample), submission);

        ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);

        //this is the important bit.  Ensures the correct values got pulled from the file
        verify(metadataTemplateService).convertMetadataStringsToSet(mapCaptor.capture());
        Map<String, MetadataEntry> metadata = mapCaptor.getValue();

        int found = 0;
        for (Map.Entry<String, MetadataEntry> e : metadata.entrySet()) {

            if (expectedResults.containsKey(e.getKey())) {
                String expected = expectedResults.get(e.getKey());

                MetadataEntry value = e.getValue();

                assertEquals("metadata values should match", expected, value.getValue());
                found++;
            }
        }
        assertEquals("should have found the same number of results", expectedResults.keySet().size(), found);

        ArgumentCaptor<Set> setCaptor = ArgumentCaptor.forClass(Set.class);
        // this bit just ensures the merged data got saved
        verify(sampleService).mergeSampleMetadata(eq(sample), setCaptor.capture());

        Set<MetadataEntry> capturedValues = setCaptor.getValue();

        assertEquals(metadataEntries.iterator().next(), capturedValues.iterator().next());
    }

    @Test
    public void testParseSpeciesAbundanceFile() throws Throwable {
        Path speciesAbundanceFilePath = Paths.get(ClassLoader.getSystemResource("species_abundance.tsv").toURI());
        Map<String, String> mostAbundantSpecies = updater.parseSpeciesAbundanceFile(speciesAbundanceFilePath);
        assertThat(mostAbundantSpecies, IsMapContaining.hasKey("name"));
        assertThat(mostAbundantSpecies, IsMapContaining.hasKey("taxonomy_lvl"));
        assertThat(mostAbundantSpecies, IsMapContaining.hasKey("taxonomy_id"));
        assertThat(mostAbundantSpecies, IsMapContaining.hasKey("fraction_total_reads"));
    }

    @Test
    public void testParseSpeciesAbundanceFileNoHeader() throws Throwable {
        Path speciesAbundanceFilePath = Paths.get(ClassLoader.getSystemResource("species_abundance_no_header.tsv").toURI());
        Map<String, String> mostAbundantSpecies = updater.parseSpeciesAbundanceFile(speciesAbundanceFilePath);
        assertThat(mostAbundantSpecies, IsMapContaining.hasKey("name"));
        assertThat(mostAbundantSpecies, IsMapContaining.hasKey("taxonomy_lvl"));
        assertThat(mostAbundantSpecies, IsMapContaining.hasKey("taxonomy_id"));
        assertThat(mostAbundantSpecies, IsMapContaining.hasKey("fraction_total_reads"));
    }
}