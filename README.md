# neodisambiguate

<!-- [![Install with bioconda](https://img.shields.io/badge/Install%20with-bioconda-brightgreen.svg)](http://bioconda.github.io/recipes/neodisambiguate/README.html) -->
<!-- [![Anaconda Version](https://anaconda.org/bioconda/neodisambiguate/badges/version.svg)](http://bioconda.github.io/recipes/neodisambiguate/README.html) -->
[![Unit Tests](https://github.com/clintval/neodisambiguate/actions/workflows/unit-tests.yml/badge.svg?branch=main)](https://github.com/clintval/neodisambiguate/actions/workflows/unit-tests.yml)
[![Java Version](https://img.shields.io/badge/java-8,11,17,21-c22d40.svg)](https://github.com/AdoptOpenJDK/homebrew-openjdk)
[![Language](https://img.shields.io/badge/language-scala-c22d40.svg)](https://www.scala-lang.org/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/clintval/neodisambiguate/blob/master/LICENSE)

Disambiguate reads that were mapped to multiple references.

![Torres del Paine](.github/img/cover.jpg)

Install with the Conda or Mamba package manager after setting your [Bioconda channels](https://bioconda.github.io/user/install.html#set-up-channels):

```text
❯ conda install neodisambiguate
```

### Introduction

Disambiguation of aligned reads is performed per-template and all information across primary, secondary, and supplementary alignments is used as evidence.
Alignment disambiguation is commonly required when analyzing sequencing data from transduction, transfection, transgenic, or xenographic (including patient derived xenograft) experiments.
This tool works by comparing various alignment scores between a template that has been aligned to many references in order to determine which reference is the most likely source.

All templates which are positively assigned to a single source reference are written to a reference-specific output BAM file.
Any templates with ambiguous reference assignment are written to an ambiguous input-specific output BAM file.
Only BAMs produced from the Burrows-Wheeler Aligner (bwa) or STAR are currently supported.

Input BAMs of arbitrary sort order are accepted, however, an internal sort to queryname will be performed unless the BAM is already in queryname sort order.
All output BAM files will be written in the same sort order as the input BAM files.
Although paired-end reads will give the most discriminatory power for disambiguation of short-read sequencing data, this tool accepts paired, single-end (fragment), and mixed pairing input data.

### Features

- Accepts SAM/BAM sources of any sort order
- Will disambiguate an arbitrary number of BAMs, all aligned to different references
- Writes the ambiguous alignments to a separate directory
- Extensible implementation which supports alternative disambiguation strategies
- Benchmarks show high accuracy: [Click Here](benchmarks/disambiguate.md)

### Command Line Usage

```bash
❯ neodisambiguate -i infile1.bam infile2.bam -p out/disambiguated
```

### Example Usage

To disambiguate templates for sample `dna00001` that are aligned to human (A) and mouse (B):

```bash
❯ neodisambiguate -i dna00001.A.bam dna00001.B.bam -p out/dna00001 -n hg38 mm10
```

```console
❯ tree out/
  out/
  ├── ambiguous-alignments/
  │  ├── dna00001.A.ambiguous.bai
  │  ├── dna00001.A.ambiguous.bam
  │  ├── dna00001.B.ambiguous.bai
  │  └── dna00001.B.ambiguous.bam
  ├── dna00001.hg38.bai
  ├── dna00001.hg38.bam
  ├── dna00001.mm10.bai
  └── dna00001.mm10.bam
```

### Local Installation

Bootstrap compilation and build the executable with:

```bash
./mill neodisambiguate.executable
./bin/neodisambiguate --help
```

### Prior Art

This project was inspired by AstraZeneca's `disambiguate`:

- https://github.com/AstraZeneca-NGS/disambiguate