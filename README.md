[![Build Status](https://travis-ci.org/Public-Health-Bioinformatics/irida-plugin-species-abundance.svg?branch=master)](https://travis-ci.org/Public-Health-Bioinformatics/irida-plugin-species-abundance)
[![GitHub release](https://img.shields.io/github/release/public-health-bioinformatics/irida-plugin-species-abundance.svg)](https://github.com/public-health-bioinformatics/irida-plugin-species-abundance/releases/latest)

# IRIDA Species Abundance Pipeline Plugin

![galaxy-workflow-diagram.png][]

This project contains a pipeline implemented as a plugin for the [IRIDA][] bioinformatics analysis system. 
This can be used to estimate the relative abundance of sequence reads originating from different species in a sample.

# Table of Contents

   * [IRIDA Species Abundance Pipeline Plugin](#irida-example-pipeline-plugin)
   * [Installation](#installation)
      * [Installing Galaxy Dependencies](#installing-galaxy-dependencies)
      * [Installing to IRIDA](#installing-to-irida)
   * [Usage](#usage)
      * [Analysis Results](#analysis-results)
      * [Metadata Table](#metadata-table)
   * [Building](#building)
      * [Installing IRIDA to local Maven repository](#installing-irida-to-local-maven-repository)
      * [Building the plugin](#building-the-plugin)
   * [Dependencies](#dependencies)

# Installation

## Installing Galaxy Dependencies

In order to use this pipeline, you will also have to install the [kraken2][] and [bracken][] Galaxy tools and their data 
managers within your Galaxy instance. These can be found at:

| Name                               | Version               | Galaxy Tool                                                                                |
|------------------------------------|-----------------------|--------------------------------------------------------------------------------------------|
| kraken2                            | `2.1.1+galaxy1`       | <https://toolshed.g2.bx.psu.edu/view/iuc/kraken2/e674066930b2>                             |
| bracken                            | `2.6.1+galaxy0`       | <https://toolshed.g2.bx.psu.edu/view/iuc/bracken/b08ac10aed96>                             |
| data_manager_build_kraken2_database| `2.1.1`               | <https://toolshed.g2.bx.psu.edu/view/iuc/data_manager_build_kraken2_database/2f27f3b86827> |
| data_manager_build_bracken_database| `2.5.1+galaxy1`       | <https://toolshed.g2.bx.psu.edu/view/iuc/data_manager_build_bracken_database/3c7d2c84cb09> |

## Installing to IRIDA

Please download the provided `irida-plugin-species-abundance-[version].jar` from the [releases][https://github.com/Public-Health-Bioinformatics/irida-plugin-species-abundance/releases]
page and copy to your  `/etc/irida/plugins` directory.  Now you may start IRIDA and you should see the pipeline appear in your list of pipelines.

*Note:* This plugin requires you to be running IRIDA version >= `21.01`. Please see the [IRIDA documentation][https://phac-nml.github.io/irida-documentation/developer/tools/pipelines/] for more details.

# Usage

The plugin should now show up in the **Analyses > Pipelines** section of IRIDA.

![plugin-pipeline.png][]  

## Analysis Results

You should be able to run a pipeline with this plugin and get analysis results. The results include a `kraken2` taxonomic
classification report, and a `bracken` estimate of the relative abundance of reads from each species in your sample.

![plugin-results.png][]

## Metadata Table

And, you should be able to save and view these results in the IRIDA metadata table. The following fields are written to
the IRIDA 'Line List':

| Field Name                       | Description                                                                  |
|----------------------------------|------------------------------------------------------------------------------|
| species-abundance/taxon_name     | The scientific name of the most abundant species in the sample               |
| species-abundance/taxonomy_level | The taxonomic level at which reads were aggregated ('S' for species)         |
| species-abundance/taxonomy_id    | The NCBI taxonomy ID for the most abundant species in the sample             |
| species-abundance/proportion     | The proportion of reads in this sample assigned to the most abundant species |


![plugin-metadata.png][]

# Building

Building and packaging this code is accomplished using [Apache Maven][maven]. However, you will first need to install [IRIDA][] to your local Maven repository. The version of IRIDA you install will have to correspond to the version found in the `irida.version.compiletime` property in the [pom.xml][] file of this project. Right now, this is IRIDA version `19.01.3`.

## Installing IRIDA to local Maven repository

To install IRIDA to your local Maven repository please do the following:

1. Clone the IRIDA project

```bash
git clone https://github.com/phac-nml/irida.git
cd irida
```

2. Checkout appropriate version of IRIDA

```bash
git checkout -b 21.01 21.01
```

3. Install IRIDA to local repository

```bash
mvn clean install -DskipTests
```

## Building the plugin

Once you've installed IRIDA as a dependency, you can proceed to building this plugin. Please run the following commands:

```bash
cd irida-plugin-species-abundance

mvn clean package
```

Once complete, you should end up with a file `target/irida-plugin-species-abundance-0.1.0.jar` which can be installed as a plugin to IRIDA.

# Dependencies

The following dependencies are required in order to make use of this plugin.

* [IRIDA][] >= 21.01
* [Java][] >= 1.8 and [Maven][maven] (for building)



[maven]: https://maven.apache.org/
[IRIDA]: http://irida.ca/
[Galaxy]: https://galaxyproject.org/
[Java]: https://www.java.com/
[kraken2]: https://github.com/DerrickWood/kraken2
[bracken]: https://github.com/jenniferlu717/Bracken
[irida-pipeline]: https://irida.corefacility.ca/documentation/developer/tools/pipelines/
[irida-pipeline-galaxy]: https://irida.corefacility.ca/documentation/developer/tools/pipelines/#galaxy-workflow-development
[irida-wf-ga2xml]: https://github.com/phac-nml/irida-wf-ga2xml
[pom.xml]: pom.xml
[workflows-dir]: src/main/resources/workflows
[workflow-structure]: src/main/resources/workflows/0.1.0/irida_workflow_structure.ga
[speciesabundance-plugin-java]: src/main/java/ca/corefacility/bioinformatics/irida/plugins/SpeciesAbundancePlugin.java
[irida-plugin-java]: https://github.com/phac-nml/irida/tree/development/src/main/java/ca/corefacility/bioinformatics/irida/plugins/IridaPlugin.java
[irida-updater]: src/main/java/ca/corefacility/bioinformatics/irida/plugins/SpeciesAbundancePluginUpdater.java
[irida-setup]: https://irida.corefacility.ca/documentation/administrator/index.html
[properties]: https://en.wikipedia.org/wiki/.properties
[messages]: src/main/resources/workflows/0.1.0/messages_en.properties
[maven-min-pom]: https://maven.apache.org/guides/introduction/introduction-to-the-pom.html#Minimal_POM
[pf4j-start]: https://pf4j.org/doc/getting-started.html
[plugin-results.png]: doc/images/plugin-results.png
[plugin-pipeline.png]: doc/images/plugin-pipeline.png
[plugin-metadata.png]: doc/images/plugin-metadata.png
[pipeline-parameters.png]: doc/images/pipeline-parameters.png
[plugin-save-results.png]: doc/images/plugin-save-results.png
[galaxy-workflow-diagram.png]: doc/images/galaxy-workflow-diagram.png
