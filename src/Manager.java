import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLCodec;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.StringACLCodec;
import jade.wrapper.AgentContainer;
import jade.wrapper.StaleProxyException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Integer.parseInt;

public class Manager extends Agent {

    @Override
    protected void setup() {
        System.out.println(getLocalName()+ " is ready");

        // Регистрация агента в сервисе каталога
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("manager");
        sd.setName("Manager");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        // Пауза для ожидания других агентов
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Добавление поведения ожидания информации от других агентов
        addBehaviour(new BlackListWaiter());
    }

    // Поведение для ожидания и обработки сообщений от других агентов
    public class BlackListWaiter extends Behaviour {
        HashMap<String, String> compukterList = new HashMap<>();

        @Override
        public void action(){
            ACLMessage msg = myAgent.receive();
            if (msg != null)
                switch (msg.getPerformative()) {
                    // Обработка подтверждения (CONFIRM) от компьютера о добавлении в черный список
                    case ACLMessage.CONFIRM:
                        compukterList.put(msg.getSender().toString(), msg.getContent());
                        break;
                    // Обработка отмены (CANCEL) от компьютера о удалении из черного списка
                    case ACLMessage.CANCEL:
                        compukterList.remove(msg.getSender().toString());
                        break;
                }
            else {
                // Поиск компьютеров в сервисе каталога
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
                // Проверка, получена ли информация от всех компьютеров
                if (result.length == compukterList.size()) {
                    // Отправка сообщения CANCEL всем компьютерам для завершения их работы
                    ACLMessage STOP_message = new ACLMessage(ACLMessage.CANCEL);
                    for (DFAgentDescription description : result) {
                        STOP_message.addReceiver(description.getName());
                    }

                    // Поиск задач в сервисе каталога
                    DFAgentDescription taskTemplate = new DFAgentDescription();
                    ServiceDescription taskSD = new ServiceDescription();
                    taskSD.setType("task");
                    taskTemplate.addServices(taskSD);

                    DFAgentDescription[] taskAgents = null;

                    try {
                        taskAgents = DFService.search(myAgent, taskTemplate);
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }

                    for (DFAgentDescription task : taskAgents) {
                        STOP_message.addReceiver(task.getName());
                    }

                    myAgent.send(STOP_message);

                    // Запись информации о компьютерах в файл "out.txt"
                    FileWriter writer = null;
                    try {
                        writer = new FileWriter("out.txt");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // Запись информации о каждом компьютере
                    for (DFAgentDescription description : result) {
                        String[] info = compukterList.get(description.getName().toString()).split("_");
                        String name = info[0];
                        String capacity = info[1];
                        String timeOfWork = info[2];

                        // Запись основной информации о компьютере
                        try {
                            writer.write(name + " s:" + capacity + " t:" + timeOfWork + "\n");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        // Запись информации о заданиях, принадлежащих компьютеру
                        for (int i = 3; i < info.length; i++) {
                            try {
                                writer.write("  " + info[i] + "\n");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        try {
                            writer.write("\n\n");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }


                    }
                    // Закрытие файла
                    try {
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
//                    AgentContainer myContainer =  myAgent.getContainerController();
//                    try {
//                        myContainer.kill();
//                    } catch (StaleProxyException e) {
//                        e.printStackTrace();
//                        System.err.println("Ошибка при уничтожении контейнера: " + e.getMessage());
//                    }
                    // Поиск агента типа "manager" в сервисном реестре
                    template = new DFAgentDescription();
                    sd = new ServiceDescription();
                    sd.setType("manager");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] res = DFService.search(myAgent, template);
                        if (res.length != 0) {
                            // Удаление агента Manager из сервисного реестра перед его завершением
                            DFService.deregister(myAgent, res[0]);
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }

                    // Завершение работы агента
                    doDelete();
                } else
                    block();
            }
        }

        @Override
        public boolean done() {
            return false;
        }
    }
}
