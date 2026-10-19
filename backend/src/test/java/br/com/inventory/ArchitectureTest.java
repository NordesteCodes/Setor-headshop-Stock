package br.com.inventory;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    @Test void domainAndApplicationDoNotDependOnAdaptersOrFrameworks(){
        var classes=new ClassFileImporter().importPackages("br.com.inventory");
        noClasses().that().resideInAnyPackage("..domain..","..application..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..","jakarta..","..adapter..","com.fasterxml..")
            .check(classes);
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..").check(classes);
    }
}
