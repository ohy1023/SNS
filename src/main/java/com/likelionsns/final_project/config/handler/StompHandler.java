package com.likelionsns.final_project.config.handler;

import com.likelionsns.final_project.service.ChatRoomService;
import com.likelionsns.final_project.service.ChatService;
import com.likelionsns.final_project.utils.JwtUtils;
import io.jsonwebtoken.MalformedJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.stereotype.Component;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;


@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 99) // 우선 순위를 높게 설정해서 SecurityFilter들 보다 앞서 실행되게 해준다.
public class StompHandler implements ChannelInterceptor {

    private final ChatRoomService chatRoomService;
    private final ChatService chatService;

    @Value("${jwt.secret}")
    private String secretKey;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        log.info("message : {}", message.toString());
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            // Disconnect 시에는 토큰 검사를 하지 않도록 처리
            log.info("DISCONNECT command received, skipping token verification.");
            return message;
        }

        String userName = verifyAccessToken(getAccessToken(accessor));
        // StompCommand에 따라서 로직을 분기해서 처리하는 메서드를 호출한다.
        handleMessage(accessor.getCommand(), accessor, userName);

        return message;
    }

    private void handleMessage(StompCommand stompCommand, StompHeaderAccessor accessor, String userName) {
        switch (stompCommand) {
            case CONNECT:
                connectToChatRoom(accessor, userName);
                break;
            case ERROR:
                throw new MessageDeliveryException("error");
            default:
                break;
        }
    }

    private String getAccessToken(StompHeaderAccessor accessor) {

        String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
        if (authorizationHeader == null) {
            log.info("chat header가 없는 요청입니다.");
            throw new MalformedJwtException("jwt");
        }

        String token;
        log.info(authorizationHeader);
        String authorizationHeaderStr = authorizationHeader.replace("[", "").replace("]", "");
        if (authorizationHeaderStr.startsWith("Bearer ")) {
            token = authorizationHeaderStr.replace("Bearer ", "");
        } else {
            log.error("Authorization 헤더 형식이 틀립니다. : {}", authorizationHeader);
            throw new MalformedJwtException("jwt");
        }
        log.info("전처리후 token : {}", token);
        return token;
    }

    private void connectToChatRoom(StompHeaderAccessor accessor, String userName) {
        // 채팅방 번호를 가져온다.
        Integer chatRoomNo = getChatRoomNo(accessor);

        // 현재 채팅방에 접속중인 인원이 있는지 확인한다.
        boolean isConnected = chatRoomService.isConnected(chatRoomNo);

        // 채팅방 입장 처리 -> Redis에 입장 내역 저장
        chatRoomService.connectChatRoom(chatRoomNo, userName);

        // 읽지 않은 채팅을 전부 읽음 처리
        chatRoomService.updateUnreadMessagesToRead(chatRoomNo, userName);

        log.info("해당 채팅방에 접속중인 유저 유무 : {}", isConnected);

        if (isConnected) {
            chatService.updateMessage(userName, chatRoomNo);
        }
    }

    private String verifyAccessToken(String accessToken) {
        if (JwtUtils.isExpired(accessToken, secretKey)) {
            throw new IllegalStateException("토큰이 만료되었습니다.");
        }

        return JwtUtils.getUserName(accessToken, secretKey);
    }

    private Integer getChatRoomNo(StompHeaderAccessor accessor) {
        return Integer.valueOf(accessor.getFirstNativeHeader("chatRoomNo"));
    }
}