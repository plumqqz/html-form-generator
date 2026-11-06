package shaif.htmlgenerator.htmlform;
import java.lang.annotation.*;

/**
 * Метаданные поля. Работает на обычных полях класса и на константах enum.
 * Приоритет: программный override > YAML > эта аннотация > автовывод.
 */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
public @interface FormField {
    String label() default "";
    String tableLabel() default "";
    FieldType type() default FieldType.AUTO;
    /** false — не рендерить в форме (в таблице остаётся). */
    boolean visible() default true;
    /** false — не рендерить в таблице (в форме остаётся). */
    boolean tableVisible() default true;
    boolean required() default false;
    boolean multiple() default false;
    boolean readonly() default false;
    boolean disabled() default false;
    EmptyMode emptyOption() default EmptyMode.AUTO;
    String[] options() default {};
    String placeholder() default "";
    String cssClass() default "";
    String pattern() default "";
    String patternMessage() default "";
    int minLength() default -1;
    int maxLength() default -1;
    String order() default "";
    String format() default "";
    // для констант enum:
    String value() default "";
    String group() default "";

    enum EmptyMode { AUTO, YES, NO }
}
