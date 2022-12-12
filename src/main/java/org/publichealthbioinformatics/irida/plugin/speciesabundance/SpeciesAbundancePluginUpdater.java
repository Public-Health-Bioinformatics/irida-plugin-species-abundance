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
	private final int NUM_SPECIES_TO_REPORT = 5;

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

			List<Map<String, String>> speciesAbundances = parseSpeciesAbundanceFile(speciesAbundanceFilePath);
			Map<String, String> unclassifiedAbundances = speciesAbundances.remove(0);
			String key;
			String value;
			PipelineProvidedMetadataEntry entry;
			value = unclassifiedAbundances.get("bracken_fraction_total_seqs");
			entry = new PipelineProvidedMetadataEntry(value, "float", analysis);
			key = workflowName + "/" + "proportion_unclassified";
			metadataEntries.put(key, entry);
			int speciesNum = 1;
			for (Map<String, String> species : speciesAbundances) {
				value = species.get("taxonomy_lvl");
				entry = new PipelineProvidedMetadataEntry(value, "text", analysis);
				// taxonomy_level is only recorded once per sample. (should be identical for all lines in a report.)
				if (speciesNum == 1) {
					key = workflowName + "/" + "taxonomy_level";
					metadataEntries.put(key, entry);
				}

				value = species.get("name");
				entry = new PipelineProvidedMetadataEntry(value, "text", analysis);
				if (speciesNum == 1) {
					key = workflowName + "/" + "taxon_name";
				} else {
					key = workflowName + "/" + "taxon_name_" + speciesNum;
				} 
				metadataEntries.put(key, entry);

				value = species.get("taxonomy_id");
				entry = new PipelineProvidedMetadataEntry(value, "text", analysis);
				if (speciesNum == 1) {
					key = workflowName + "/" + "taxonomy_id";
				} else {
					key = workflowName + "/" + "taxonomy_id_" + speciesNum;
				}
				metadataEntries.put(key, entry);

				value = species.get("bracken_fraction_total_seqs");
				entry = new PipelineProvidedMetadataEntry(value, "float", analysis);
				if (speciesNum == 1) {
					key = workflowName + "/" + "proportion";
				} else {
					key = workflowName + "/" + "proportion_" + speciesNum;
				}
				metadataEntries.put(key, entry);
				speciesNum++;
			}
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
	 *                      name	taxonomy_id	taxonomy_lvl	kraken_assigned_seqs	bracken_assigned_seqs	total_seqs	kraken_fraction_total_seqs	bracken_fraction_total_seqs
	 *                      Salmonella enterica	28901	S	228436	841169	848138	0.269338	0.991783
	 *                      </pre>
	 *
	 * @return A {@link Map<String, String>} containing the read count.
	 * @throws IOException If there was an error reading the file.
	 */
	@VisibleForTesting
	List<Map<String, String>> parseSpeciesAbundanceFile(Path speciesAbundanceFilePath) throws IOException {
		BufferedReader speciesAbundanceReader = new BufferedReader(new FileReader(speciesAbundanceFilePath.toFile()));
		List<Map<String, String>> abundances = new ArrayList<>();
		ArrayList<String> expectedHeaderFields = new ArrayList<String>(Arrays.asList(
		        "name",
		        "taxonomy_id",
		        "taxonomy_lvl",
		        "kraken_assigned_seqs",
		        "bracken_assigned_seqs",
		        "total_seqs",
		        "kraken_fraction_total_seqs",
		        "bracken_fraction_total_seqs"
		));
		try {
			String headerLine = speciesAbundanceReader.readLine();
			ArrayList<String> headerFields = new ArrayList<String>(Arrays.asList(headerLine.split("\t")));
			String abundancesLine;
			if (!headerFields.equals(expectedHeaderFields)) { // header not present
					abundancesLine = headerLine;
					headerFields = expectedHeaderFields;
			} else {
				abundancesLine = speciesAbundanceReader.readLine();
				ArrayList<String> speciesAbundanceFields = new ArrayList<String>(Arrays.asList(abundancesLine.split("\t")));
				Map<String, String> speciesAbundanceMap = new HashMap<>();
				assert headerFields.size() == speciesAbundanceFields.size();
				for (int j = 0; j < headerFields.size(); j++) {
					speciesAbundanceMap.put(headerFields.get(j), speciesAbundanceFields.get(j));
				}
				abundances.add(speciesAbundanceMap);
			}
			for (int i = 0; i < NUM_SPECIES_TO_REPORT; i++) {
				abundancesLine = speciesAbundanceReader.readLine();
				ArrayList<String> speciesAbundanceFields = new ArrayList<String>(Arrays.asList(abundancesLine.split("\t")));
				Map<String, String> speciesAbundanceMap = new HashMap<>();
				assert headerFields.size() == speciesAbundanceFields.size();
				for (int j = 0; j < headerFields.size(); j++) {
					speciesAbundanceMap.put(headerFields.get(j), speciesAbundanceFields.get(j));
				}
				abundances.add(speciesAbundanceMap);
			}
		} finally {
			speciesAbundanceReader.close();
		}

		return abundances;
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
