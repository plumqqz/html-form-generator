package shaif.htmlgenerator.htmlform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlMapFilesystemTest {

    @Test
    void loadYamlForMapFromFilesystem(@TempDir Path tempDir) throws IOException {
        // 1. YAML без text blocks, так как это Java 11
        String yamlContent = String.join(System.lineSeparator(),
                "fields:",
                "  email:",
                "    label: \"Электронная почта\"",
                "    required: true",
                "    cssClass: \"form-control\"",
                "  amount:",
                "    label: \"Сумма\"",
                "    cssClass: \"form-control text-right\""
        );

        // 2. Пишем YAML во временный файл
        Path yamlFile = tempDir.resolve("payment-form.yaml");
        Files.writeString(yamlFile, yamlContent);

        // 3. Данные для Map-модели
        Map<String, Object> data = Map.of(
                "email", "user@example.com",
                "amount", 1000
        );

        // 4. Загружаем YAML из файловой системы
        Map spec = YamlSpec.load(yamlFile);

        // 5. Генерируем HTML-форму
        String html = FormGen.ofData(data)
                .yaml(spec)
                .form("/save", "post");

        // 6. Проверяем, что настройки из YAML применились
        assertAll("HTML должен содержать настройки из YAML",
                () -> assertTrue(html.contains("name=\"email\"")),
                () -> assertTrue(html.contains("required")),
                () -> assertTrue(html.contains("class=\"form-control\"")),
                () -> assertTrue(html.contains("Электронная почта")),
                () -> assertTrue(html.contains("name=\"amount\"")),
                () -> assertTrue(html.contains("class=\"form-control text-right\"")),
                () -> assertTrue(html.contains("Сумма"))
        );
    }
}