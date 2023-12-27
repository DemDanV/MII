import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLCodec;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.StringACLCodec;

import java.io.StringReader;
import java.util.HashSet;

import static java.lang.Integer.parseInt;

public class TaskAgent extends Agent {

    int complexity;

    @Override
    protected void setup() {
        System.out.println("Task " + getLocalName()+" is ready");

        Object[] args = getArguments();

        if (args != null && args.length == 2) {
            complexity = parseInt(args[0].toString()); // Извлекаем сложность задания из аргументов

        } else {
            /* Удаляем агента если не заданы параметры */
            this.takeDown();
        }

        // Регистрация агента в сервисном реестре
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("task");
        sd.setName("MyTask");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        addBehaviour(new InitBehaviour());// Добавление поведения инициализации
    }

    // Поведение инициализации агента
    public class InitBehaviour extends OneShotBehaviour{

        // Отправка сообщений менеджеру и компьютеру о своём создании
        @Override
        public void action() {
            // Ищем компьютер
            AID compukter = null;
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("compukter");
            template.addServices(sd);
            try {
                DFAgentDescription[] results = null;
                while (results == null || results != null && results.length < 1) {
                    results = DFService.search(myAgent, template);
                }

                // Выбираем первый подходящий компьютер
                for (DFAgentDescription result : results)
                {
                    String compName = result.getName().getLocalName();
                    if (checkCompatibility(compName, getLocalName()))
                        compukter = result.getName();
                }

                if (compukter == null)
                {
                    System.out.println(getLocalName() + " не нашлось подходящих компьютеров");
                    return;
                }

            } catch (FIPAException fe) {
                fe.printStackTrace();
            }

            // Отправляем сообщение компьютеру
            ACLMessage message = new ACLMessage(ACLMessage.PROPAGATE);
            message.addReceiver(compukter);
            message.setContent(String.valueOf(complexity) + "_" + getLocalName());
            myAgent.send(message);

            // Добавляем поведение изменения компьютера
            addBehaviour(new ChangeCompukterBehaviour());
        }
    }

    //Поведение изменения компьютера. Когда совершается обмен
    // компьютер просит агент задания прикрепиться к другому
    public class ChangeCompukterBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage reply = myAgent.receive();
            if (reply != null) {
                if (reply.getPerformative() == ACLMessage.CANCEL) {
                    // Обработка сообщения CANCEL
                    // Например, отменить подписку или выполнить другие необходимые действия
                    System.out.println(getLocalName() + " умер");
                    doDelete();
                }
                else
                {
                    StringACLCodec codec = new StringACLCodec(new StringReader(reply.getContent()), null);
                    AID compukter = null;
                    try {
                        compukter = codec.decodeAID();
                    } catch (ACLCodec.CodecException e) {
                        e.printStackTrace();
                    }

                    // Отправляем сообщение компьютеру о подписке на задание
                    ACLMessage message = new ACLMessage(ACLMessage.SUBSCRIBE);
                    message.addReceiver(compukter);
                    message.setContent(String.valueOf(complexity) + "_" + getLocalName());
                    myAgent.send(message);
                    System.out.println("Задание " + getLocalName() + " прикреплено к " + compukter.getLocalName());
                }
            } else
                block(); // Блокировка поведения, если сообщение не получено
        }

    }

    // Проверка совместимости требований задания и возможностей компьютера
    public boolean checkCompatibility(String compName, String taskName)
    {
        String taskRequirements = taskName.substring(taskName.indexOf("-") + 1);
        String compProvide = compName.substring(compName.indexOf("-") + 1);

        if (taskRequirements.length() != compProvide.length()) {
            System.out.println(compName + " или " + taskName + " ошибка в свойствах");
            this.takeDown();
        }

        for (int i = 0; i < taskRequirements.length(); i++)
            if (taskRequirements.charAt(i) == '1' && compProvide.charAt(i) == '0')
                return false;
        return true;
    }

    // Метод, вызываемый при завершении работы агента
    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }
}
