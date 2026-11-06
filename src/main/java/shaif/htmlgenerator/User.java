package shaif.htmlgenerator;
import shaif.htmlgenerator.htmlform.FormField;
import shaif.htmlgenerator.htmlform.FormIgnore;
import java.time.LocalDate;
import java.util.List;
public class User {
    private Long id;
    @FormField(required = true, order = "001") private String firstName;
    @FormField(required = true, order = "002") private String lastName;
    private String middleName;
    private LocalDate birthDate;
    private Gender gender;
    private String email, phone;
    private String country, city, role, department;
    private List<String> permissions;
    private boolean active;

    @FormIgnore
    private String internalNote;

    public enum Gender {
        @FormField(label = "Мужской") M,
        @FormField(label = "Женский") F
    }
    public Long getId() { return id; }
    public String getFirstName() { return firstName; } public String getLastName() { return lastName; }
    public String getEmail() { return email; } public String getPhone() { return phone; }
    public String getCountry() { return country; } public String getCity() { return city; }
    public String getRole() { return role; } public String getDepartment() { return department; }
    public User setId(Long v) { id = v; return this; }
    public User setFirstName(String v) { firstName = v; return this; }
    public User setLastName(String v) { lastName = v; return this; }
    public User setMiddleName(String v) { middleName = v; return this; }
    public User setBirthDate(LocalDate v) { birthDate = v; return this; }
    public User setGender(Gender v) { gender = v; return this; }
    public User setEmail(String v) { email = v; return this; }
    public User setPhone(String v) { phone = v; return this; }
    public User setCountry(String v) { country = v; return this; }
    public User setCity(String v) { city = v; return this; }
    public User setRole(String v) { role = v; return this; }
    public User setDepartment(String v) { department = v; return this; }
    public User setPermissions(List<String> v) { permissions = v; return this; }
    public User setActive(boolean v) { active = v; return this; }
}
