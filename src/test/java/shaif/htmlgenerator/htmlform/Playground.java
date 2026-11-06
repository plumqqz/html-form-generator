package shaif.htmlgenerator.htmlform;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Playground {
    @Test
    void innerClassTest(){
        class Inner{
            String email;
            String gender;
        }
        var inner = new Inner();
        var html=FormGen.of(inner)
                .yaml(YamlSpec.loadFromClasspath("User.yaml", Locale.forLanguageTag("ru")))
                .options("gender", "m=Male", "f=Female")
                .form("/save-email", "post");
        System.out.println("html="+html);

        Map<String,Object> map = Map.of("name", "name", "email", "lala@dodo.com", "source", "zz");
        var html2=FormGen.ofData(map)
                .yaml(YamlSpec.loadFromClasspath("User.yaml", Locale.forLanguageTag("ru")))
                .yaml(Map.of("fields",Map.of("source", Map.of("label", "###SOURCE###", "emptyOption",false, "order","900"))))
                .options("gender", "m=Male", "f=Female")
                .options("source", ctx -> {
                   System.out.println("Class:"+ctx.fieldName);
                   return List.of(new Option("lala","dood"), new Option("zz","zz"));
                })
                .form("/save-email", "post");
        System.out.println("html2="+html2);
    }
}
