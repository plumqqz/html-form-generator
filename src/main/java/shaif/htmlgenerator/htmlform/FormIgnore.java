package shaif.htmlgenerator.htmlform;
import java.lang.annotation.*;

/**
 * Полностью игнорировать поле при генерации формы и таблицы.
 * Поле не попадёт ни в форму, ни в таблицу, ни в список контролов.
 */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
public @interface FormIgnore {}
