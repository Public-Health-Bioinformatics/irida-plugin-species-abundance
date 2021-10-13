package org.publichealthbioinformatics.irida.plugin.speciesabundance;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import ca.corefacility.bioinformatics.irida.exceptions.IridaWorkflowNotFoundException;
import ca.corefacility.bioinformatics.irida.exceptions.PostProcessingException;
import ca.corefacility.bioinformatics.irida.model.sample.Sample;
import ca.corefacility.bioinformatics.irida.model.sample.metadata.MetadataEntry;
import ca.corefacility.bioinformatics.irida.model.sample.metadata.PipelineProvidedMetadataEntry;
import ca.corefacility.bioinformatics.irida.model.workflow.IridaWorkflow;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.AnalysisOutputFile;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.type.AnalysisType;
import ca.corefacility.bioinformatics.irida.model.workflow.submission.AnalysisSubmission;
import ca.corefacility.bioinformatics.irida.pipeline.results.updater.AnalysisSampleUpdater;
import ca.corefacility.bioinformatics.irida.service.sample.MetadataTemplateService;
import ca.corefacility.bioinformatics.irida.service.sample.SampleService;
import ca.corefacility.bioinformatics.irida.service.workflow.IridaWorkflowsService;

import com.google.common.annotations.VisibleForTesting;

/**
 * This implements a class used to perform post-processing on the analysis
 * pipeline results to extract information to write into the IRIDA metadata
 * tables. Please see
 * <https://github.com/phac-nml/irida/blob/development/src/main/java/ca/corefacility/bioinformatics/irida/pipeline/results/AnalysisSampleUpdater.java>
 * or the README.md file in this project for more details.
 */
public class SpeciesAbundancePluginUpdater implements AnalysisSampleUpdater {

	private final MetadataTemplateService metadataTemplateService;
	private final SampleService sampleService;
	private final IridaWorkflowsService iridaWorkflowsService;

	/**
	 * Builds a new {@link SpeciesAbundancePluginUpdater} with the given services.
	 *
	 * @param metadataTemplateService The metadata template service.
	 * @param sampleService           The sample service.
	 * @param iridaWorkflowsService   The irida workflows service.
	 */
	public SpeciesAbundancePluginUpdater(MetadataTemplateService metadataTemplateService, SampleService sampleService,
			IridaWorkflowsService iridaWorkflowsService) {
		this.metadataTemplateService = metadataTemplateService;
		this.sampleService = sampleService;
		this.iridaWorkflowsService = iridaWorkflowsService;
	}

	/**
	 * Code to perform the actual update of the {@link Sample}s passed in the
	 * collection.
	 *
	 * @param samples  A collection of {@link Sample}s that were passed to this
	 *                 pipeline.
	 * @param analysis The {@link AnalysisSubmission} object corresponding to this
	 *                 analysis pipeline.
	 */
	@Override
	public void update(Collection<Sample> samples, AnalysisSubmission analysis) throws PostProcessingException {
		if (samples == null) {
			throw new IllegalArgumentException("samples is null");
		} else if (analysis == null) {
			throw new IllegalArgumentException("analysis is null");
		} else if (samples.size() != 1) {
			// In this particular pipeline, only one sample should be run at a time so I
			// verify that the collection of samples I get has only 1 sample
			throw new IllegalArgumentException(
					"samples size=" + samples.size() + " is not 1 for analysisSubmission=" + analysis.getId());
		}

		// extract the 1 and only sample (if more than 1, would have thrown an exception
		// above)
		final Sample sample = samples.iterator().next();

		// extracts paths to the analysis result files
		AnalysisOutputFile speciesAbundanceFile = analysis.getAnalysis().getAnalysisOutputFile("species_abundance");
		Path speciesAbundanceFilePath = speciesAbundanceFile.getFile();

		try {
			Map<String, MetadataEntry> metadataEntries = new HashMap<>();

			// get information about the workflow (e.g., version and name)
			IridaWorkflow iridaWorkflow = iridaWorkflowsService.getIridaWorkflow(analysis.getWorkflowId());
			String workflowVersion = iridaWorkflow.getWorkflowDescription().getVersion();
			String workflowName = iridaWorkflow.getWorkflowDescription().getName();

			Map<String, String> mostAbundantSpecies = parseSpeciesAbundanceFile(speciesAbundanceFilePath);

			String mostAbundantSpeciesName = mostAbundantSpecies.get("name");
			PipelineProvidedMetadataEntry mostAbundantSpeciesNameEntry = new PipelineProvidedMetadataEntry(mostAbundantSpeciesName, "text", analysis);
			String mostAbundantSpeciesNameKey = workflowName + "/" + "taxon_name";
			metadataEntries.put(mostAbundantSpeciesNameKey, mostAbundantSpeciesNameEntry);

			String mostAbundantSpeciesTaxonomyLevel = mostAbundantSpecies.get("taxonomy_lvl");
			PipelineProvidedMetadataEntry mostAbundantSpeciesTaxonomyLevelEntry = new PipelineProvidedMetadataEntry(mostAbundantSpeciesTaxonomyLevel, "text", analysis);
			String mostAbundantSpeciesTaxonomyLevelKey = workflowName + "/" + "taxonomy_level";
			metadataEntries.put(mostAbundantSpeciesTaxonomyLevelKey, mostAbundantSpeciesTaxonomyLevelEntry);

			String mostAbundantSpeciesTaxonomyId = mostAbundantSpecies.get("taxonomy_id");
			PipelineProvidedMetadataEntry mostAbundantSpeciesTaxonomyIdEntry = new PipelineProvidedMetadataEntry(mostAbundantSpeciesTaxonomyId, "text", analysis);
			String mostAbundantSpeciesTaxonomyIdKey = workflowName + "/" + "taxonomy_id";
			metadataEntries.put(mostAbundantSpeciesTaxonomyIdKey, mostAbundantSpeciesTaxonomyIdEntry);

			String mostAbundantSpeciesProportionTotalReads = mostAbundantSpecies.get("fraction_total_reads");
			PipelineProvidedMetadataEntry mostAbundantSpeciesProportionTotalReadsEntry = new PipelineProvidedMetadataEntry(mostAbundantSpeciesProportionTotalReads, "float", analysis);
			String mostAbundantSpeciesProportionTotalReadsKey = workflowName + "/" + "proportion";
			metadataEntries.put(mostAbundantSpeciesProportionTotalReadsKey, mostAbundantSpeciesProportionTotalReadsEntry);

			//convert the string/entry Map to a Set of MetadataEntry
			Set<MetadataEntry> metadataSet = metadataTemplateService.convertMetadataStringsToSet(metadataEntries);

			// merges with existing sample metadata and does an update of the sample metadata.
			sampleService.mergeSampleMetadata(sample,metadataSet);
		} catch (IOException e) {
			throw new PostProcessingException("Error parsing species abundance file", e);
		} catch (IridaWorkflowNotFoundException e) {
			throw new PostProcessingException("Could not find workflow for id=" + analysis.getWorkflowId(), e);
		}
	}

	/**
	 * Parses out the read count from the passed file.
	 *
	 * @param speciesAbundanceFilePath The file containing the species abundance. The file contents
	 *                      should look like:
	 *
	 *                      <pre>
	 *                      name	taxonomy_id	taxonomy_lvl	kraken_assigned_reads	added_reads	new_est_reads	fraction_total_reads
	 *                      Salmonella enterica	28901	S	433515	32457	465972	0.99016
	 *                      </pre>
	 *
	 * @return A {@link Map<String, String>} containing the read count.
	 * @throws IOException If there was an error reading the file.
	 */
	@VisibleForTesting
	Map<String, String> parseSpeciesAbundanceFile(Path speciesAbundanceFilePath) throws IOException {
		BufferedReader speciesAbundanceReader = new BufferedReader(new FileReader(speciesAbundanceFilePath.toFile()));
		Map<String, String> mostAbundantSpecies = new HashMap<>();
		ArrayList<String> expectedHeaderFields = new ArrayList<String>(Arrays.asList(
				"name",
		        "taxonomy_id",
		        "taxonomy_lvl",
		        "kraken_assigned_reads",
		        "added_reads",
		        "new_est_reads",
		        "fraction_total_reads"
		));
		try {
			String headerLine = speciesAbundanceReader.readLine();
			ArrayList<String> headerFields = new ArrayList<String>(Arrays.asList(headerLine.split("\t")));
			String mostAbundantSpeciesLine;
			if (!headerFields.equals(expectedHeaderFields)) { // header not present
					mostAbundantSpeciesLine = headerLine;
					headerFields = expectedHeaderFields;
			} else {
				mostAbundantSpeciesLine = speciesAbundanceReader.readLine();
			}
			ArrayList<String> mostAbundantSpeciesFields = new ArrayList<String>(Arrays.asList(mostAbundantSpeciesLine.split("\t")));
			assert headerFields.size() == mostAbundantSpeciesFields.size();
			for (int i = 0; i < headerFields.size(); i++) {
				mostAbundantSpecies.put(headerFields.get(i), mostAbundantSpeciesFields.get(i));
			}
		} finally {
			speciesAbundanceReader.close();
		}

		return mostAbundantSpecies;
	}

	/**
	 * The {@link AnalysisType} this {@link AnalysisSampleUpdater} corresponds to.
	 *
	 * @return The {@link AnalysisType} this {@link AnalysisSampleUpdater}
	 *         corresponds to.
	 */
	@Override
	public AnalysisType getAnalysisType() {
		return SpeciesAbundancePlugin.SPECIES_ABUNDANCE;
	}
}
