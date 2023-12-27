import jade.core.AID;

// Класс, представляющий задание
public class Task {
    public AID aid;             // Идентификатор агента, связанного с заданием
    public int complexity = 0;  // Сложность задания
    public String name;         // Название задания

    // Конструктор класса, инициализирующий поля задания
    public Task(AID id, int comp, String _name) {
        aid = id;
        complexity = comp;
        name = _name;
    }
}
