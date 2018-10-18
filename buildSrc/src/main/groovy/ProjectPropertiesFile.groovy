/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class ProjectPropertiesFile extends DefaultTask {

    private String destPackage = ''
    private String filename = defaultFilename()
    private List<Property> properties = new ArrayList<>()

    @OutputFile
    File getOutputFile() {
        String outputFile = "${project.projectDir}/src/main/java/${packagePath()}/${filename}.java"
        return project.file(outputFile)
    }

    @TaskAction
    void generateFile() {
        getOutputFile().text = generateFileContent()
    }

    void setDestPackage(String destPackage) {
        this.destPackage = destPackage
    }

    @Input
    String getDestPackage() {
        return destPackage
    }

    void setFilename(String filename) {
        this.filename = filename
    }

    @Input
    String getFilename() {
        return filename
    }

    void addString(String name, String value) {
        properties.add(new Property(name, value, PropertyType.STRING))
    }

    @Nested
    List<Property> getProperties() {
        return properties
    }

    private String packagePath() {
        return destPackage.replace(".", "/")
    }

    private String defaultFilename() {
        return "${project.name.capitalize()}Info"
    }

    private String generateFileContent() {
        String[] varDeclarations = properties.stream().map({p -> p.variableDeclaration()}).toArray()
        String[] methodDeclarations = properties.stream().map({p -> p.methodDeclaration()}).toArray()
        return """package ${destPackage};

// This file is generated via a gradle task and should not be edited directly.
public class ${filename} {
${String.join("\n  ", varDeclarations)}

  private ${filename}() {}
${String.join("\n", methodDeclarations)}
}
"""
    }

    private enum PropertyType {
        STRING("String")

        private final String strVal
        PropertyType(String strVal) {
            this.strVal = strVal
        }

        String toString() {
            return strVal
        }
    }

    private static class Property {
        private final String name
        private final String value
        private final PropertyType type

        Property(name, value, type) {
            this.name = name
            this.value = value
            this.type = type
        }

        @Input
        String getName() {
            return name
        }

        @Input
        String getValue() {
            return value
        }

        @Input
        String getType() {
            return type.toString()
        }

        String variableDeclaration() {
            return "  private static final ${type} ${name} = \"${value}\";"
        }

        String methodDeclaration() {
            return """
  public static ${type} ${name}() {
    return ${name};
  }"""
        }
    }
}
