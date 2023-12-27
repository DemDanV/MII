import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

import static java.lang.Integer.parseInt;

public class CompukterAgent extends Agent {
    ArrayList<Task> taskAgentList=new ArrayList<>();
    ArrayList<Double> taskUnitCostList = new ArrayList<>();

    int capacity;



    @Override
    protected void setup() {
        System.out.println("Compukter " + getLocalName()+" is ready");

        Object[] args = getArguments();

        // Проверка и установка параметров агента
        if (args != null && args.length == 2) {
            capacity = parseInt(args[0].toString());

        } else {
            // Удаляем агента, если не заданы параметры
            this.takeDown();
        }

        // Регистрация агента в сервисном реестре
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("compukter");
        sd.setName("myCompukter");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
//        try {
//            Thread.sleep(20000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        // Добавление поведения Requester
        this.addBehaviour(new Requester());

    }

    public class Requester extends Behaviour {
        // Компьютеры, с которыми не состоялся обмен
        HashSet<String> blackList = new HashSet<>();
        private int step = 0;

        Boolean STOP = false;

        private int tasksExpected = 0; // Число агентов, которые должны придти к нам после обмена (шаг 2)
        private int tasksReceived = 0;
        private AID tradeAgent = null; // Агент, с которым мы меняемся, нужно чтобы не отправлять ему уведомление об обновлении
        //0 - обычный режим (принимаем НОВЫЕ ДЛЯ ВСЕХ задания, обновляем черный список, ищем доступные компы)
        //1 - ждем ответа на наше предложение обмена, забиваем на всё остальное
        //2 - принимаем задания, которые хотят к нам приписаться после обмена

        @Override
        public void action() {
            switch(step){
            case 0:
                // Обработка различных типов сообщений
                MessageTemplate mtp = MessageTemplate.or(MessageTemplate.or(MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.PROPAGATE),
                        MessageTemplate.MatchPerformative(ACLMessage.CFP)), MessageTemplate.MatchPerformative(ACLMessage.PROPOSE)),
                        MessageTemplate.MatchPerformative(ACLMessage.CANCEL));
                ACLMessage msg = myAgent.receive(mtp);
                if (msg != null) {
                    switch (msg.getPerformative()) {
                        // При типе сообщения PROPAGATE добавляем агент к компьютеру
                        case ACLMessage.PROPAGATE:
                            AddTaskFromMsg(msg);

                            // Уведомление всех компьютеров, кроме указанного, о добавлении нового задания
                            NotifyAllCompuktersExcept(null);
                            break;
                        // При CFP удаляем комп, который обновился, из ЧС
                        case ACLMessage.CFP:
                            blackList.remove(msg.getSender().toString());
                            NotifyManager(false);
                            break;
                        case ACLMessage.PROPOSE:
                            PerformExchange(msg);
                            break;
                        case ACLMessage.CANCEL:
                            System.out.println(getLocalName() + " умер");
                            Print();
                            STOP = true;
                            doDelete();
                            break;
                    }
                } else {
                    if(taskAgentList.size() == 0)
                    {
                        block();
                        return;
                    }
                    //если сообщений нет то ищем компы не из черного списка
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("compukter");
                    template.addServices(sd);
                    DFAgentDescription[] result = null;
                    try {
                        result = DFService.search(myAgent, template);
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                    AID firstSuitableComp = null;
                    //ищем рандомный подходящий комп
                    ArrayList<Integer> indexes = new ArrayList<>();
                    for (int i = 0; i < result.length; i++) {
                        AID res = result[i].getName();
                        if (!blackList.contains(res.toString()) && !res.toString().equals(myAgent.getAID().toString()))
                            indexes.add(i);
                    }
                    //если черный список забит останавливаем агента
                    if (indexes.size() == 0) NotifyManager(true);
                    Random rand = new Random();
                    if (indexes.size() > 0) firstSuitableComp = result[indexes.get(rand.nextInt(indexes.size()))].getName();
                    if (firstSuitableComp != null) {
//                        System.out.println(getLocalName() + " нечего делать предлагает обмен " + firstSuitableComp.getLocalName());
                        ProposeExchange(firstSuitableComp);
                    }
                    else
                        block();
                }
                break;
            case 1:
                //ждем сообщения согласия на согласие или непринятие обмена, также отказываем всем кто хочет поменяться
                MessageTemplate mt =
                        MessageTemplate.or(MessageTemplate.or(MessageTemplate.or(
                                                MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL),
                                                MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL)),
                                                MessageTemplate.MatchPerformative(ACLMessage.REFUSE)),
                                                MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
                ACLMessage reply = myAgent.receive(mt);

                if (reply != null)
                    switch(reply.getPerformative()) {
                        case ACLMessage.ACCEPT_PROPOSAL:
                            blackList.add(reply.getSender().toString());
                            String repl = reply.getContent();
                            String typeOfTrade = repl.substring(0, repl.indexOf("_"));
                            int val = Integer.parseInt(repl.substring(repl.indexOf("_") + 1));
                            //если нам отсылают задания готовимся принять
                            if (typeOfTrade.equals("getReady")) {
                                tasksExpected = val;
                                tradeAgent = reply.getSender();
                                step = 2;
                            }
                            //если просят нас отсылаем задания и возвращаемся в обычный режим
                            if (typeOfTrade.equals("sendMe")) {
                                ACLMessage req_to_tasks_msg = new ACLMessage();
                                req_to_tasks_msg.setContent(reply.getSender().toString());
                                //рассылаем сообщения заданиям чтобы они приписались к другому агенту и удаляем их у себя
                                ArrayList<Integer> taskIndexes = new ArrayList<>();
                                for (int i = 0, j = 0; i < taskAgentList.size() && j < val; i++)
                                    if (checkCompatibility(reply.getSender().getLocalName(), taskAgentList.get(i).name))
                                    {
                                        taskIndexes.add(i);
                                        j++;
                                    }

                                for (int index : taskIndexes)
                                    req_to_tasks_msg.addReceiver(taskAgentList.get(index).aid);

                                //удаляем отданные задания у себя
                                taskIndexes.stream()
                                        .sorted(Comparator.reverseOrder())
                                        .forEach(i->taskAgentList.remove(i.intValue()));
                                taskIndexes.stream()
                                        .sorted(Comparator.reverseOrder())
                                        .forEach(i->taskUnitCostList.remove(i.intValue()));

                                step = 0;
                                myAgent.send(req_to_tasks_msg);

                                NotifyAllCompuktersExcept(reply.getSender());
                            }
                            break;
                        case ACLMessage.REJECT_PROPOSAL:
                            blackList.add(reply.getSender().toString());
                            step = 0;
                            break;
                        case ACLMessage.REFUSE:
                            step = 0;
                            break;
                        case ACLMessage.PROPOSE:
                            ACLMessage reject_msg = new ACLMessage(ACLMessage.REFUSE);
                            reject_msg.addReceiver(reply.getSender());
                            myAgent.send(reject_msg);
                            //когда остается всего два агента они начинают синхронно отправлять друг другу предложения,
                            // из-за чего процесс затягивается, так он затягиваться не будет
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            break;
                    }
                else
                    block();
                break;
            case 2:
                // Обработка сообщений SUBSCRIBE или PROPOSE
                MessageTemplate Mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.SUBSCRIBE),
                        MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
                ACLMessage trade_mes = myAgent.receive(Mt);
                if (trade_mes != null)
                switch (trade_mes.getPerformative()) {
                    case ACLMessage.SUBSCRIBE:
                        tasksReceived++;

                        AddTaskFromMsg(trade_mes);

                        //если получили все задания возвращаемся в обычный режим
                        if (tasksReceived == tasksExpected) {
                            tasksReceived = 0;
                            step = 0;
                            NotifyAllCompuktersExcept(tradeAgent);
                        }
                        break;
                    case ACLMessage.PROPOSE:
                        ACLMessage reject_msg = new ACLMessage(ACLMessage.REFUSE);
                        reject_msg.addReceiver(trade_mes.getSender());
                        myAgent.send(reject_msg);
                        break;
                }
                else
                    block();
                break;
            }
        }

        private void AddTaskFromMsg(ACLMessage msg) {
            // Получение информации о задании из содержания сообщения
            String str = msg.getContent();
            int cap = parseInt(str.substring(0, str.indexOf("_")));
            String name = str.substring(str.indexOf("_") + 1);

            // Получение идентификатора отправителя сообщения (AID)
            AID task_aid = msg.getSender();

            // Создание объекта Task на основе полученных данных
            Task task = new Task(task_aid, cap, name);

            // Добавление задания в список заданий агента
            taskAgentList.add(task);

            // Вычисление и добавление относительной стоимости задания к ёмкости агента
            taskUnitCostList.add(task.complexity / (double)capacity);

            // Сортировка списка заданий по сложности
            taskAgentList.sort(new Comparator<Task>() {
                @Override
                public int compare(Task o1, Task o2) {
                    return Integer.compare(o1.complexity, o2.complexity);
                }
            });

            // Сортировка списка относительных стоимостей заданий
            taskUnitCostList.sort(new Comparator<Double>() {
                @Override
                public int compare(Double o1, Double o2) {
                    return o1.compareTo(o2);
                }
            });
        }

        public void Print() {
            // StringBuilder для хранения сложности каждого задания
            StringBuilder outp = new StringBuilder();
            // Проход по каждому заданию в taskAgentList
            for (Task tsk : taskAgentList) {
                // Добавление сложности каждого задания в StringBuilder
                outp.append(tsk.complexity).append(" ");
            }
            // StringBuilder для хранения затрат на каждое задание
            StringBuilder outpp = new StringBuilder();
            // Переменная для хранения суммарных затрат на задания
            double sum = 0;

            // Проход по каждым затратам на задание в taskUnitCostList
            for (double k : taskUnitCostList) {
                // Добавление форматированных затрат на каждое задание в StringBuilder
                outpp.append(String.format(Locale.US,"%.2f", k)).append(" ");

                // Добавление затрат на задание к сумме
                sum += k;
            }
            // Вывод информации о заданиях агента
            System.out.println(getLocalName() + " N: " + taskUnitCostList.size() + " t: " + String.format(Locale.US,"%.2f", sum) + " contains:" + outp);
        }

        public void PerformExchange(ACLMessage msg) {
            // Вывод информации о попытке обмена между агентами
            System.out.println("ПОПЫТКА ОБМЕНА " + getLocalName() + "<--->" + msg.getSender().getLocalName());

            // Добавление агента в черный список, чтобы избежать повторного обмена
            blackList.add(msg.getSender().toString());

            // Разбор содержимого сообщения от другого агента
            String[] input = msg.getContent().split(" ");
            List<Integer> ext_tasks_complexity = new ArrayList<>();
            List<Double> ext_unit_cost = new ArrayList<>();
            int ext_capacity = parseInt(input[0]);
            double ext_TimeOfWork = Double.parseDouble(input[1]);

            // Заполнение списков сложности заданий и их затрат от другого агента
            for (int i = 2; i < input.length; i++) {
                int k = parseInt(input[i]);
                ext_tasks_complexity.add(k);
                ext_unit_cost.add(k / (double) ext_capacity);
            }

            // Получение текущего времени работы текущего агента
            double our_TimeOfWork = getTimeOfWork();


            // Если текущий агент работает больше, чем другой, попытка передачи заданий другому агенту
            if (ext_TimeOfWork < our_TimeOfWork) {
                int index_of_last_checked_task = 0;
                int size = taskAgentList.size();
                List<Integer> taskIndexes = new ArrayList<>();

                // Попытка отдать задания с учетом изменения времени работы
                while (our_TimeOfWork > ext_TimeOfWork && index_of_last_checked_task < size) {
                    //предполагаем как изменится время если мы отдадим задании
                    if (checkCompatibility(msg.getSender().getLocalName(), taskAgentList.get(index_of_last_checked_task).name)) {
                        double ext_comp_gain = taskAgentList.get(index_of_last_checked_task).complexity / (double) ext_capacity;
                        double our_comp_loss = taskUnitCostList.get(index_of_last_checked_task);
                        our_TimeOfWork -= our_comp_loss;
                        ext_TimeOfWork += ext_comp_gain;
                        taskIndexes.add(index_of_last_checked_task);
                    }

                    index_of_last_checked_task++;

                }

                // Предварительная проверка, стоит ли выкидывать последнее взятое задание
                if (taskIndexes.size() > 0) {
                    double ext_comp_gain = taskAgentList.get(taskIndexes.get(taskIndexes.size() - 1)).complexity / (double) ext_capacity;
                    double our_comp_loss = taskUnitCostList.get(taskIndexes.get(taskIndexes.size() - 1));
                    if (Math.abs(our_TimeOfWork + our_comp_loss - ext_TimeOfWork + ext_comp_gain) < Math.abs(our_TimeOfWork - ext_TimeOfWork))
                    {
                        taskIndexes.remove(taskIndexes.size() - 1);
                    }
                }

                // Если есть подходящие задания, отправляем уведомление другому агенту
                if (taskIndexes.size() > 0) {
                    // Отправка сообщения, чтобы агент готовился к приему заданий
                    ACLMessage accept_message = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    accept_message.setContent("getReady_" + taskIndexes.size());
                    accept_message.addReceiver(msg.getSender());
                    myAgent.send(accept_message);

                    ACLMessage message = new ACLMessage();
                    message.setContent(msg.getSender().toString());
                    // Рассылка сообщений заданиям для перепривязки к другому агенту
                    for (int index : taskIndexes)
                        message.addReceiver(taskAgentList.get(index).aid);

                    // Удаление отданных заданий у текущего агента
                    taskIndexes.stream()
                            .sorted(Comparator.reverseOrder())
                            .forEach(i->taskAgentList.remove(i.intValue()));
                    taskIndexes.stream()
                            .sorted(Comparator.reverseOrder())
                            .forEach(i->taskUnitCostList.remove(i.intValue()));

                    // Отправка сообщения другому агенту
                    myAgent.send(message);
                    System.out.println("ОБМЕН " + getLocalName() + "--->" + msg.getSender().getLocalName());

                    // Уведомление всех о своих изменениях
                    NotifyAllCompuktersExcept(msg.getSender());
                    return;
                }
            } else
                // Если текущий агент работает меньше, чем другой, попытка получения заданий от другого агента
                if (ext_TimeOfWork > our_TimeOfWork) {
                int index_of_last_suitable_task = 0;
                int size = ext_unit_cost.size();

                // Попытка взять задание с учетом изменения времени работы
                while (our_TimeOfWork < ext_TimeOfWork && index_of_last_suitable_task < size) {
                    //предполагаем как изменится время если мы ВОЗЬМЕМ задание
                    double ext_comp_loss = ext_unit_cost.get(index_of_last_suitable_task);
                    double our_comp_gain = ext_tasks_complexity.get(index_of_last_suitable_task) / (double) capacity;

                        our_TimeOfWork += our_comp_gain;
                        ext_TimeOfWork -= ext_comp_loss;
                        index_of_last_suitable_task++;


                }

                // Предварительная проверка, стоит ли выкидывать последнее взятое задание
                if (size > 0) {
                    double ext_comp_loss = ext_unit_cost.get(index_of_last_suitable_task - 1);
                    double our_comp_gain = ext_tasks_complexity.get(index_of_last_suitable_task - 1) / (double) capacity;
                    if (Math.abs(ext_TimeOfWork + ext_comp_loss - our_TimeOfWork + our_comp_gain) < Math.abs(ext_TimeOfWork - our_TimeOfWork))
                        index_of_last_suitable_task--;
                }
                // Если есть подходящее задание, отправляем уведомление другому агенту
                if (index_of_last_suitable_task > 0) {
                    // Отправка сообщения, чтобы агент отправил нам задания, сами готовимся к их приему
                    ACLMessage accept_message = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    accept_message.setContent("sendMe_" + index_of_last_suitable_task);
                    accept_message.addReceiver(msg.getSender());
                    tradeAgent = msg.getSender();
                    tasksExpected = index_of_last_suitable_task;
                    step = 2;
                    System.out.println("ОБМЕН " + getLocalName() + "<---" + msg.getSender().getLocalName());
                    myAgent.send(accept_message);
                    return;
                }
            }
            // Если ничего подходящего не найдено, отправляем REJECT
            System.out.println("ПОПЫТКА " + getLocalName() + "<--->" + msg.getSender().getLocalName() + " НЕ УДАЛАСЬ");
            ACLMessage reject_message = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
            reject_message.addReceiver(msg.getSender());
            myAgent.send(reject_message);
        }

        // Метод для предложения обмена заданиями другому агенту
        public void ProposeExchange(AID comp) {
            // Создание сообщения типа PROPOSE
            ACLMessage message = new ACLMessage(ACLMessage.PROPOSE);
            StringBuilder myinfo = new StringBuilder(capacity + " " + getTimeOfWork() + " ");

            // Формирование информации о текущих заданиях, совместимых с другим агентом
            for (Task task : taskAgentList)
                if (checkCompatibility(comp.getLocalName(), task.name))
                    myinfo.append(task.complexity).append(" ");

            // Установка содержимого сообщения и адресата
            message.setContent(myinfo.toString());
            message.addReceiver(comp);
            step = 1; // Установка шага для перехода к обработке ответа на предложение
            myAgent.send(message);
        }

        // Метод для проверки совместимости свойств компьютера и задания
        public boolean checkCompatibility(String compName, String taskName)
        {
            String taskRequirements = taskName.substring(taskName.indexOf("-") + 1);
            String compProvide = compName.substring(compName.indexOf("-") + 1);

            if (taskRequirements.length() != compProvide.length()) {
                System.out.println(compName + " или " + taskName + " ошибка в свойствах");
            }

            for (int i = 0; i < taskRequirements.length(); i++)
                if (taskRequirements.charAt(i) == '1' && compProvide.charAt(i) == '0')
                    return false;
            return true;
        }

        // Уведомление всех компьютеров, кроме переданного, о своем обновлении (вызываем когда делаем обмен или добавляем задание)
        public void NotifyAllCompuktersExcept(AID excludedAgent) {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("compukter");
            template.addServices(sd);
            DFAgentDescription[] result = null;
            try {
                result = DFService.search(myAgent, template);
            } catch (FIPAException fe) {
                fe.printStackTrace();
            }

            // Создание сообщения CFP и добавление получателей
            ACLMessage message = new ACLMessage(ACLMessage.CFP);
            for (DFAgentDescription description : result)
                if (!description.getName().toString().equals(myAgent.getAID().toString()) && (excludedAgent == null ||
                        excludedAgent != null && !description.getName().toString().equals(excludedAgent.toString()))) message.addReceiver(description.getName());

            // Вывод информации о состоянии
            Print();

            // Отправка сообщения
            myAgent.send(message);
        }

        // Уведомление менеджера о состоянии блэклиста
        public void NotifyManager(boolean isBlackListFull) {
            // Создание сообщения для менеджера
            ACLMessage msg = null;
            if (isBlackListFull) {
                msg = new ACLMessage(ACLMessage.CONFIRM);
                StringBuilder tasks = new StringBuilder();
                tasks.append(getLocalName() + "_" + capacity + "_" + getTimeOfWork() + "_");
                for (Task task : taskAgentList)
                    tasks.append(task.complexity + " " + task.name + "_");
                msg.setContent(String.valueOf(tasks));
            } else {
                msg = new ACLMessage(ACLMessage.CANCEL);
            }

            // Поиск и добавление менеджера в получатели
            AID manager = null;
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("manager");
            template.addServices(sd);
            try {
                DFAgentDescription[] res = DFService.search(myAgent, template);
                manager = res[0].getName();
            } catch (FIPAException fe) {
                fe.printStackTrace();
            }
            msg.addReceiver(manager);

            // Отправка сообщения
            myAgent.send(msg);
        }

        // Метод, определяющий завершение выполнения агента
        @Override
        public boolean done() {
            return STOP;
        }
    }

    // Метод для расчета суммарного времени работы
    public double getTimeOfWork(){
        double sum_complexity = 0;
        for (Task task: taskAgentList)
            sum_complexity += task.complexity;
        return sum_complexity/capacity;
    }

    // Метод вызывается при завершении работы агента для отмены регистрации в службе каталога
    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }
}
