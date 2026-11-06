# html-form-generator

Генератор HTML-форм и таблиц из Java-классов или `Map<String,Object>`.
Java 11+. Пакет: `shaif.htmlgenerator`.

## О библиотеке

### Зачем

Типичная CRUD-админка на Java состоит из десятков однотипных страниц:
список записей, карточка, форма редактирования. Каждая форма — это 20–40
полей, у каждого метка, тип контрола, валидация, права доступа, расположение,
ошибки. Писать это руками — часы копипасты, при изменении модели — ещё раз
часы. Существующие решения (Thymeleaf-шаблоны, JSP-теги, Wicket, Vaadin)
либо требуют отдельного DSL для каждой формы, либо тянут за собой целый
фреймворк и его жизненный цикл.

Библиотека закрывает одну задачу: **рендеринг HTML-формы или таблицы из
существующей модели данных** — без собственного шаблонизатора, без серверного
состояния, без привязки к фреймворку. Модель — обычный Java-класс (или
`Map<String,Object>`) со значениями. Что рендерить — решает библиотека по
типам полей и небольшому конфигу (аннотация + YAML). Как выглядит — решает
пользователь: стандартные раскладки (`VERTICAL`/`HORIZONTAL`/`INLINE`) или
своя вёрстка поверх `controls()`.

### Что делает и чего не делает

| Делает | Не делает |
|---|---|
| Отрендерит `<form>` из класса/map | Не валидирует данные |
| Отрендерит `<table>` из списка | Не сортирует, не пагинирует |
| Выведет `<select>` из `enum`, YAML, `List<Option>`, лямбды | Не ходит в БД за опциями |
| Отметит ошибки/предупреждения полей и глобальные | Не решает, что является ошибкой |
| Учтёт `readonly`, `disabled`, `required`, `pattern`, `cssClass` | Не знает про права доступа |
| Поставит CSRF-токен | Не генерирует и не проверяет токен |
| Даст список `Control`/`Column` для своей вёрстки | Не навязывает структуру макета |

Граница проходит по **ответственности за данные**:

- что показать — решает **модель** (ваш Java-объект);
- как показать — решает **YAML и аннотация** (или `controls()`);
- откуда взять значения и опции — решает **вызывающий код**.

Библиотека — **между моделью и браузером**: она берёт объект и превращает
его в HTML. Всё, что «до» (валидация, права, БД) и «после» (POST-обработка,
сохранение) — зона приложения.

### Общий принцип

Ядро — три вещи:

1. **`FormGen`** собирает **список** `Control` (или `Column` для таблицы).
   Каждый `Control` знает свой `name`, `label`, `html()`, `errors()`,
   `warnings()`, `readonly`, `disabled`. Список — снапшот, ничего не
   мутирует в генераторе.

2. **Рендереры раскладок** (`VERTICAL`/`HORIZONTAL`/`INLINE`) обходят
   этот список и оборачивают контролы в `<div class="field">`/`<label>`/
   `<span>`. Разница между раскладками — только обёртка, контролы и их
   порядок одинаковы.

3. **`YamlSpec`** мержит YAML из classpath (база + локаль + `extends`)
   в один `Map`, который `FormGen` накладывает на аннотации. YAML — не
   отдельный DSL рендеринга, а способ **переопределить** дефолты аннотаций
   без пересборки Java-кода.

Всё остальное (`errors`, `warnings`, `csrf`, `readonly`, `formId`) — тонкие
fluent-методы поверх этих трёх вещей.

## Содержание

- [Сборка](#сборка)
- [Быстрый старт](#быстрый-старт)
- [API](#api)
- [Типы полей](#типы-полей)
- [Две метки](#две-метки)
- [Источники опций](#источники-опций)
- [Пустая опция](#пустая-опция)
- [CSS-класс](#css-класс)
- [Упорядочивание](#упорядочивание)
- [readonly и disabled](#readonly-и-disabled)
- [Ошибки и предупреждения](#ошибки-и-предупреждения)
- [Форма: id, атрибуты, CSRF](#форма-id-атрибуты-csrf)
- [Раскладки](#раскладки)
- [Модель контролов](#модель-контролов)
- [Таблицы](#таблицы)
- [YAML](#yaml)
- [POST-цикл](#post-цикл)
- [Cookbook](#cookbook)
- [Демо](#демо)
- [Файлы библиотеки](#файлы-библиотеки)
- [Что не поддерживается](#что-не-поддерживается)

## Сборка

    mvn -q test                 # 96 тестов
    mvn -q clean compile exec:java    # демо → target/demo/

## Быстрый старт

```java
FormGen.of(User.class).form();
FormGen.of(user).form("/save", "post", "Сохранить");
FormGen.ofData(Map.of("name", "John", "age", 30)).form();

FormGen.of(User.class).table(rows)
    .link("/users/{id}")
    .link("email", "mailto:{_}")
    .action("Edit", u -> "/users/" + ((User) u).getId() + "/edit")
    .empty("нет записей")
    .html();
```

## API

### Создание

| Метод | Что даёт |
|---|---|
| `FormGen.of(Class<?>)` | Поля по рефлексии, значений нет |
| `FormGen.of(Object)` | Поля по рефлексии, значения из объекта |
| `FormGen.ofData(Map<String,Object>)` | Поля по ключам map, тип — по значению |

### Тюнинг

| Метод | Назначение |
|---|---|
| `.label(field, text)` | Метка поля в форме (fallback для таблицы) |
| `.tableLabel(field, text)` | Метка колонки в таблице |
| `.cssClass(field, text)` | CSS-класс на контроле |
| `.readonly(field)` / `.disabled(field)` | Только чтение / отключён |
| `.options(field, …)` | Литералы / `List<Option>` / varargs / лямбда |
| `.formId(id)` | `<form id="…">` |
| `.formAttr(name, value)` | Атрибут `<form>`; `value == null` → без значения |
| `.csrf(token)` / `.csrf(name, token)` | Скрытый CSRF-контрол |
| `.errors(Map<...>)` / `.errors(List<...>)` | Ошибки полей / глобальные |
| `.warnings(Map<...>)` / `.warnings(List<...>)` | Предупреждения |
| `.yaml(spec)` | Наложить YAML (можно цепочкой) |
| `.form([layout, action, method, submit])` | Отрендерить форму |
| `.controls()` | Все контролы (видимые и скрытые) |
| `.visibleControls()` | Только видимые |
| `.hiddenControls()` | Только `<input type="hidden">` |
| `.mixin(Class)` | Подмешать аннотации к полям |
| `.table(rows)` → `.html()` | Отрендерить таблицу |

## Типы полей

`FieldType`: `AUTO, TEXT, TEXTAREA, NUMBER, EMAIL, PASSWORD, CHECKBOX, RADIO,
SELECT, DATE, TIME, DATETIME, HIDDEN, TEL, URL`.

`AUTO` — определить по Java-типу: `boolean → CHECKBOX`, `enum → SELECT`,
`LocalDate → DATE`, `LocalDateTime → DATETIME`, `int/long/BigDecimal → NUMBER`,
остальное — `TEXT`.

## Две метки

Приоритет для таблицы (сверху вниз):

1. `.tableLabel(field, …)`
2. `tableLabel:` в YAML
3. `.label(field, …)` (fallback)
4. `label:` в YAML
5. `@FormField(label = …)`
6. `humanize(fieldName)`

Для формы — симметрично, без шагов 1 и 2.

## Источники опций

| Способ | Где задаётся | Когда использовать |
|---|---|---|
| Константы `enum` | Java | Фиксированный набор |
| `@FormField(options = …)` | Аннотация | Несколько статических |
| `options:` в YAML | Конфиг | Статический вне кода |
| `.options(field, "v=l", …)` / `.options(field, List<Option>)` | Java | Список в памяти |
| `.options(field, ctx -> …)` | Java | Динамика: БД, сессия |

Если у поля нет источника опций — тип остаётся `TEXT`, рендерится как `<input>`.

### ctx.owner(Class<T>)

Типизированный владелец для лямбд-провайдеров:

```java
.options("city", ctx -> {
    User u = ctx.owner(User.class);
    return u == null || u.getCountry() == null
        ? Collections.emptyList()
        : dao.cities(u.getCountry());
})
```

## Пустая опция

Отключается через `emptyOption: false` в YAML или `@FormField(emptyOption = FormField.EmptyMode.NO)`.

## CSS-класс

Три источника: `.cssClass()` > `cssClass:` в YAML > `@FormField(cssClass = …)`.

## Упорядочивание

`order` — **строка**, сортируется лексикографически. Поля без `order` — в конце.

```yaml
fields:
  firstName:  { order: "001" }
  middleName: { order: "001.005" }
  lastName:   { order: "002" }
```

В YAML — обязательно в кавычках: `order: 001` → `Integer(1)` → строка `"1"`.

## readonly и disabled

Три источника: `.readonly(f)`/`.disabled(f)`, YAML `readonly:`/`disabled:`,
`@FormField(readonly = …)`.

`readonly` не рендерится на `<select>`, `<input type="checkbox">`, `<input type="radio">`
(HTML5 не поддерживает). `disabled` — везде.

**Семантика:** `readonly` — виден и отправляется. `disabled` — виден, но
**не отправляется**.

## @FormIgnore

```java
public class User {
    @FormIgnore
    private String internalNote;
}
```

Поле с `@FormIgnore` **не попадает никуда**: ни в форму, ни в таблицу, ни в
`controls()`. Отличие от `visible = false` (скрывает только в форме) и
`tableVisible = false` (скрывает только в таблице).

## Ошибки и предупреждения

Библиотека **не валидирует**. Принимает готовые сообщения и рендерит:

```java
Map<String, List<String>> errors = new LinkedHashMap<>();
if (user.getEmail() == null || user.getEmail().isBlank())
    errors.computeIfAbsent("email", k -> new ArrayList<>()).add("Required");
if (repo.emailExists(user.getEmail()))
    errors.computeIfAbsent("email", k -> new ArrayList<>()).add("Already taken");
if (!user.getPassword().equals(user.getConfirm()))
    errors.put("", List.of("Пароли не совпадают"));   // "" = глобальная

String html = FormGen.of(user).errors(errors).form("/save", "post");
```

### API

```java
FormGen.of(user)
    .errors(Map.of("email", List.of("Required", "Invalid email")))
    .warnings(Map.of("password", List.of("Истекает через 3 дня")))
    .errors(List.of("Пароли не совпадают"))
    .warnings(List.of("Данные сохранены час назад"))
    .form("/save", "post");
```

**Каждый вызов заменяет** предыдущее значение того же уровня (поля / глобальные).

HTML: `<div class="field has-error">`, `<span class="error">…</span>` под контролом;
глобальные — `<div class="form-errors" role="alert">` перед полями.

## Форма: id, атрибуты, CSRF

```java
FormGen.of(user)
    .formId("user-edit")
    .formAttr("novalidate", null)
    .formAttr("autocomplete", "off")
    .csrf("tok-123")
    .form("/save", "post");
// → <form id="user-edit" novalidate autocomplete="off">
//     <input type="hidden" name="_csrf" value="tok-123"/>
```

`csrf("name", "token")` — своё имя. `csrf("tok").csrfHtml()` — готовая строка.

## Раскладки

`Layout.VERTICAL` (по умолчанию), `HORIZONTAL`, `INLINE`. Раскладка — только
стиль; порядок и контролы одинаковы.

## Модель контролов

`controls()` — список, `visibleControls()`/`hiddenControls()` — срезы.

```java
for (FormGen.Control c : FormGen.of(user).controls()) {
    c.name; c.label; c.required; c.readonly; c.disabled;
    c.html(); c.isHidden(); c.errors(); c.warnings();
}

// своя раскладка — свой цикл
for (FormGen.Control c : FormGen.of(user).visibleControls()) { … }

// кастомный контрол
FormGen.Control photo = FormGen.Control.of("photo", "Фото",
    "<input type=\"file\" name=\"photo\"/>");
```

## Mixin — аннотации для чужого класса

Класс из сторонней библиотеки? Добавить `@FormField`/`@FormIgnore` нельзя.
Регистрируем **mixin** — класс с теми же полями, но с аннотациями:

```java
// Целевой класс: сторонний, менять нельзя
public class ThirdPartyUser {
    public Long id;
    public String email;
    public String passwordHash;
}

// Mixin — только для аннотаций
abstract class ThirdPartyUserMixin {
    @FormField(label = "Электронная почта", required = true)
    String email;

    @FormIgnore
    String passwordHash;
}

FormGen.of(ThirdPartyUser.class)
    .mixin(ThirdPartyUserMixin.class)
    .form();
```

**Как работает:**

- Для каждого поля целевого класса ищется одноимённое поле в mixin'ах.
- Если у поля в mixin'е есть `@FormField` — он **полностью перекрывает** аннотацию целевого поля.
- Если есть `@FormIgnore` — поле игнорируется (как обычно).
- Порядок mixin'ов: последний побеждает. Можно `extends`-ом наследовать mixin'ы друг от друга.

**Поле не появится**, если его нет в целевом классе — mixin не создаёт виртуальных полей.

**`Map`-модель** — mixin'ы не работают (`FormGen.ofData(...)`), потому что полей там нет.

## Таблицы

```java
FormGen.of(User.class).table(rows)
    .caption("Users").footer("Total: 2", "", "", "")
    .zebra()
    .rowClass(u -> u.isActive() ? "row-active" : "row-inactive")
    .cellClass("email", "text-muted")
    .link("/users/{id}")
    .link("email", "mailto:{_}")
    .action("Edit", u -> "/users/" + ((User) u).getId() + "/edit")
    .html();
```

`columns()` — для своей вёрстки: `col.label`, `col.cell(row)`.

## YAML

### Ключи поля

| Ключ | Тип | По умолчанию | Описание |
|---|---|---|---|
| `label` | string | humanize | Метка формы |
| `tableLabel` | string | = label | Метка колонки |
| `type` | FieldType | AUTO | Тип контрола |
| `visible` | bool | true | `false` — не в форме (в таблице остаётся) |
| `tableVisible` | bool | true | `false` — не в таблице |
| `required` | bool | false | |
| `multiple` | bool | false | |
| `readonly` / `disabled` | bool | false | |
| `emptyOption` | bool | AUTO | |
| `cssClass` | string | — | |
| `options` | list | — | `["v"]` / `["v=Label"]` |
| `placeholder` | string | — | |
| `pattern` / `patternMessage` | string | — | |
| `minLength` / `maxLength` | int | — | |
| `order` | string | "" | лексикографический |
| `extends` | string \| list | — | полевой extends |

### Прямой Map вместо YAML-файла

Если не хочется создавать YAML-файл ради одной формы, `yaml(...)` принимает `Map`:

```java
FormGen.of(user).yaml(Map.of(
    "method", "post",
    "fields", Map.of(
        "email",    Map.of("required", true, "pattern", "[^@\\\\s]+@[^@\\\\s]+\\\\.[^@\\\\s]{2,}"),
        "password", Map.of("type", "PASSWORD", "minLength", 8)
    ))).form("/save", "post");
```

Структура та же, что в YAML-файле. Ключи — те же. Программное переопределение через
`Map` поверх файла работает без ограничений:

```java
FormGen.of(user)
    .yaml(YamlSpec.loadFromClasspath(User.class, locale))
    .yaml(Map.of("fields", Map.of("email", Map.of("required", false))))   // переопределили
    .form();
```

### Ключи поля — регексы

Ключ в `fields:` — **регулярное выражение**, полное совпадение (`Pattern.matches`),
регистрозависимо. `firstName` работает как обычное имя; `.*Id$` — правило для
группы полей; `email|contactEmail` — только эти два.

**Приоритет — только порядок объявления:**

- в одном файле — сверху вниз;
- в списке файловых `extends` — слева направо;
- между `.yaml(spec)` — в порядке вызовов.

Что встретилось позже — применилось позже — перезаписало.

### Пример: разбор одного поля

```yaml
fields:
  firstName:    { label: Имя, order: "010" }     # 1
  .*Id$:        { type: HIDDEN }                 # 2
  userId:       { type: TEXT }                   # 3
  email:        { label: Email }                 # 4
```

Разбор для поля `userId`:

| # | Ключ | Матчит `userId`? | Что делает | Накопленный результат |
|---|---|---|---|---|
| 1 | `firstName` | ❌ | — | — |
| 2 | `.*Id$` | ✅ | `type = HIDDEN` | `type=HIDDEN` |
| 3 | `userId` | ✅ | `type = TEXT` | `type=TEXT` (перезаписал) |
| 4 | `email` | ❌ | — | `type=TEXT` |

Итог: `type = TEXT`. Строка 3 «победила», потому что она **ниже** строки 2.

**Если поменять порядок:**

```yaml
fields:
  userId:  { type: TEXT }      # 1
  .*Id$:   { type: HIDDEN }    # 2
```

Итог для `userId`: `type = HIDDEN` — последнее перезаписало.

**Что матчится на самом деле:**

| Поле | `firstName` | `.*Id$` | `userId` | `email` |
|---|---|---|---|---|
| `userId` | ❌ | ✅ | ✅ | ❌ |
| `orderId` | ❌ | ✅ | ❌ | ❌ |
| `firstName` | ✅ | ❌ | ❌ | ❌ |
| `email` | ❌ | ❌ | ❌ | ✅ |

Хотите «защитить» конкретное поле от общего правила — объявите его
**после** общего правила. Хотите «применить ко всем Id, включая userId» —
объявите `.*Id$` последним.

**Имя поля с `$`** (допустимо в Java, но редко): экранируйте в ключе —
`\\$field` или `\Q$field\E`.

**Полевой `extends`.** Родитель ищется по **литеральному** ключу в `fields`:

```yaml
fields:
  baseId:  { pattern: "\\d+", maxLength: 10 }
  .*Id$:   { extends: baseId, required: true }
```

Поле `userId` получит `required: true` и `pattern/maxLength` из `baseId`
(после разрешения `extends`). Сам `baseId` не рендерится — такого поля
нет в модели.

## Разрешение имён YAML

Файлы рядом с классом: `User.yaml`, `User_ru.yaml`, `User_en.yaml`,
`User_ru_RU.yaml`. Локаль накладывается на базу через deep merge.

**Не пишите в `_ru.yaml` файловый `extends`** — цикл.

## extends

Два механизма: **полевой** (`field: { extends: other }`) — внутри файла;
**файловый** (`extends: OtherFile` или `extends: [A, B]`) — на уровне файла.

### Файловый extends от нескольких файлов

```yaml
# Order.yaml
extends: [Person, Finance]
fields:
  number: { ... }
  status: { ... }
```

**YAML — это реестр описаний полей, а не набор полей.**

- `extends` мержит **словари описаний**, а не «множества полей объекта».
- Поля, которых нет в модели, **безопасно игнорируются**. Если у `Order` нет
  `bankName`, описание `bankName` из `Finance` просто не применится.
- Порядок в списке — **слои уточнения**, а не «какие типы мне нужны»: сначала
  общее, потом специфичное, потом свои поля.

**Практический вывод:** можно держать один большой `FieldLibrary.yaml` и
подключать его в любую форму:

```yaml
# User.yaml
extends: FieldLibrary
fields:
  role: { type: SELECT, options: [...] }
```

У `User` нет `orgName` — описание `orgName` проигнорируется. Ничего чистить
не нужно.

**Единственный риск — коллизия имён.** Если `Person` и `Company` описывают
одно имя (`name`) с разными `pattern`, победит тот, кто в `extends` **позже**.

### Что побеждает

- **Полевой** extends: собственные ключи > первый родитель > второй > …
- **Файловый** extends: скаляры из дочернего > поля дочернего > поля родителя.

## POST-цикл

```java
@PostMapping("/users/save")
public String save(@ModelAttribute User user, Model model, CsrfToken token) {
    Map<String, List<String>> errors = new LinkedHashMap<>();
    if (user.getEmail() == null || user.getEmail().isBlank())
        errors.computeIfAbsent("email", k -> new ArrayList<>()).add("Required");
    if (repo.emailExists(user.getEmail()))
        errors.computeIfAbsent("email", k -> new ArrayList<>()).add("Already taken");

    if (!errors.isEmpty()) {
        model.addAttribute("html", FormGen.of(user).yaml(spec)
            .csrf(token.getParameterName(), token.getToken())
            .errors(errors).form("/users/save", "post"));
        return "user-form";
    }
    repo.save(user);
    return "redirect:/users";
}
```

## Cookbook

Готовые рецепты. Каждый — минимальный рабочий код, не псевдокод.

### 1. Простейшая форма

```java
String html = FormGen.of(User.class).form();
```

### 2. Форма с YAML и локалью

```java
String html = FormGen.of(user)
    .yaml(YamlSpec.loadFromClasspath(User.class, Locale.forLanguageTag("ru")))
    .form("/save", "post");
```

### 3. Ошибки из БД

```java
Map<String, List<String>> errors = new LinkedHashMap<>();
if (repo.emailExists(user.getEmail()))
    errors.put("email", List.of("Already taken"));
String html = FormGen.of(user).errors(errors).form("/save", "post");
```

### 4. CRUD-страница: список + форма + кнопки

```java
String table = FormGen.of(User.class).table(users)
    .action("Edit", u -> "/users/" + ((User) u).getId() + "/edit")
    .action("Delete", u -> "/users/" + ((User) u).getId() + "/delete", "btn btn-danger")
    .html();

String form = FormGen.of(user).form("/users/" + user.getId(), "post");

model.addAttribute("tableHtml", table);
model.addAttribute("formHtml", form);
```

### 5. Каскад «страна → город»

```java
FormGen.of(user)
    .options("country", dao.countries())
    .options("city", ctx -> {
        User u = ctx.owner(User.class);
        return u == null || u.getCountry() == null
            ? Collections.emptyList()
            : dao.cities(u.getCountry());
    })
    .form("/save", "post");
```

При изменении страны в браузере — AJAX (см. раздел «Что не поддерживается»).

### 6. Форма с загрузкой файла

```java
List<FormGen.Control> cs = new ArrayList<>(FormGen.of(user).visibleControls());
cs.add(2, FormGen.Control.of("avatar", "Аватар",
    "<input type=\"file\" name=\"avatar\" accept=\"image/*\"/>"));

StringBuilder sb = new StringBuilder(
    "<form method=\"post\" enctype=\"multipart/form-data\">");
for (FormGen.Control c : cs) {
    sb.append("<div><label>").append(FormGen.esc(c.label)).append("</label>")
      .append(c.html()).append("</div>");
}
sb.append("<button>Сохранить</button></form>");
```

Библиотека не знает про файлы — `enctype` и `<input type="file">` — ваша зона.

### 7. Поле, видимое только для админа

```java
FormGen g = FormGen.of(user);
if (!currentUser.isAdmin()) {
    g.yaml(Map.of("fields", Map.of("role", Map.of("visible", false))));
}
g.form("/save", "post");
```

Программный `yaml(Map)` поверх файла — лучший способ добавить условную логику.
Для совсем сложных случаев — `FormGen.Control.of(...)` и своя вёрстка.

### 8. Своя раскладка (метка слева, кнопки справа)

```java
StringBuilder sb = new StringBuilder("<form method=\"post\" class=\"my-form\">\n");
for (FormGen.Control c : FormGen.of(user).visibleControls()) {
    sb.append("  <div class=\"row\">\n")
      .append("    <label class=\"col\">").append(FormGen.esc(c.label))
      .append(c.required ? " *" : "").append("</label>\n")
      .append("    <div class=\"col\">").append(c.html()).append("</div>\n")
      .append("  </div>\n");
}
sb.append("  <div class=\"actions\"><button>Save</button></div>\n</form>");
```

### 9. Ошибки из SQLException (триггер БД)

```java
Map<String, List<String>> errors = new LinkedHashMap<>();
try {
    repo.save(user);
} catch (SQLException e) {
    if (e.getMessage().contains("email_unique"))
        errors.put("email", List.of("Email уже занят"));
    else if (e.getMessage().contains("status_immutable"))
        errors.put("", List.of("Статус нельзя изменить"));
    else throw new RuntimeException(e);
}
if (!errors.isEmpty()) {
    model.addAttribute("html", FormGen.of(user).errors(errors).form("/save", "post"));
    return "user-form";
}
return "redirect:/users";
```

### 10. Mixin для стороннего класса

```java
abstract class UserMixin {
    @FormField(label = "Email", required = true) String email;
    @FormIgnore String passwordHash;
}

String html = FormGen.of(ThirdPartyUser.class)
    .mixin(UserMixin.class)
    .form("/save", "post");
```

### 11. Прямой Map вместо YAML-файла

```java
String html = FormGen.of(user).yaml(Map.of(
    "fields", Map.of(
        "email",    Map.of("required", true),
        "password", Map.of("type", "PASSWORD", "minLength", 8)
    ))).form("/save", "post");
```

### 12. Тест, где нужно только проверить HTML

```java
@Test void emailIsRequired() {
    String html = FormGen.of(User.class)
        .yaml(Map.of("fields", Map.of("email", Map.of("required", true))))
        .form();
    assertTrue(html.contains("required"));
}
```

### 13. YAML для Map-модели (без класса-якоря)
```java
Map<String, Object> data = new LinkedHashMap<>();
data.put("email", "user@example.com");
data.put("amount", "125000.00");

// Из строки
String html = FormGen.ofData(data)
    .yaml(YamlSpec.loadFromString("""
        fields:
          email:  { label: Почта, required: true }
          amount: { label: Сумма, cssClass: "form-control text-right" }
        """))
    .form("/save", "post");

// Из файла на диске (extends разрешается относительно директории файла)
Map<String, Object> spec = YamlSpec.load(Path.of("config/Payment.yaml"));
String html2 = FormGen.ofData(data).yaml(spec).form("/save", "post");

// Из classpath по явному пути
Map<String, Object> spec2 = YamlSpec.loadFromClasspath("shaif/htmlgenerator/Payment", Locale.ENGLISH);
```

## Демо

`mvn -q exec:java` создаёт в `target/demo/`:

- `form-vertical-{ru,en}.html`, `form-horizontal-{ru,en}.html`, `form-inline-{ru,en}.html`
- `form-with-errors-{ru,en}.html` — ошибки/CSRF/readonly/disabled
- `table-{ru,en}.html`
- `form-payment-map.html` — из `Map<String,Object>`
- `form-order-ru.html` — `extends: [Person, Finance]`
- `form-tradein-ru.html` — сложная форма, кастомный рендер
- `form-regex-demo.html` — регексы в ключах YAML

## Файлы библиотеки

`shaif.htmlgenerator.htmlform`: `Layout`, `FieldType`, `FormField`,
`FormIgnore`, `Option`, `FieldConfig`, `FormGen`, `YamlSpec`.

`shaif.htmlgenerator.OptionDao` — пример JDBC.

## Что не поддерживается

- **Каскадные опции** — через `ctx -> ...`, не в YAML.
- **Динамические опции из БД** — через провайдеры.
- **Вложенные структуры** (`items[0].sku`) — плоская модель.
- **Виртуальные поля** — только в классе/Map.
