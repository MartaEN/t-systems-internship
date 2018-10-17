package com.marta.logistika.tableau.beans;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.TextMessage;

@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "logiweb.update"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic")
})
public class TopicSubscriberBean implements MessageListener {

    @Inject
    private WebsocketPushBean pushBean;


    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof TextMessage) {
                String input = ((TextMessage) message).getText();
                System.out.println("TextMessage received: " + input);
                pushBean.pushUpdate(input);
            } else if (message instanceof ObjectMessage) {
                System.out.println("ObjectMessage received.");
                pushBean.pushUpdate(message.getBody(Object.class));
            } else {
                System.out.println("Unknown message type:" + message.getClass());
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }
}
