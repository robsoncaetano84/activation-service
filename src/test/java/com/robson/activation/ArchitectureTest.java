package com.robson.activation;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.robson.activation", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // api e infrastructure ficam nas pontas; application só conhece o domínio; o domínio não conhece ninguém
    @ArchTest
    static final ArchRule camadasSoDependemParaDentro = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Api").definedBy("..api..")
            .layer("Aplicacao").definedBy("..application..")
            .layer("Dominio").definedBy("..domain..")
            .layer("Infraestrutura").definedBy("..infrastructure..")
            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infraestrutura").mayNotBeAccessedByAnyLayer()
            .whereLayer("Aplicacao").mayOnlyBeAccessedByLayers("Api", "Infraestrutura")
            .whereLayer("Dominio").mayOnlyBeAccessedByLayers("Api", "Aplicacao", "Infraestrutura");

    @ArchTest
    static final ArchRule pacotesSemDependenciaCiclica = slices()
            .matching("com.robson.activation.(*)..")
            .should().beFreeOfCycles();
}
