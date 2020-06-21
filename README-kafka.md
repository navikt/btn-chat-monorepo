# kafka test

# Kafka
Start using docker-compose: `docker-compose up` from this directory.

Verify by visiting `http://localhost:3030/` 

# Producer
URL: `http://localhost:7070/`

Send message:
```javascript
fetch('/send/abba1231', {
  method: 'POST',
  body: "Your message here"
});
```

# Consumer
URL: `http://localhost:7071/`

Setup WS:
```javascript
var socket = new WebSocket('ws://localhost:7075/btn-chat-fss/api/chat/abba1231');

// Connection opened
socket.addEventListener('open', function (event) {
    socket.send('Hello Server!');
});

// Listen for messages
socket.addEventListener('message', function (event) {
    console.log('Message from server ', event.data);
});

socket.addEventListener('close', function(e) { console.log('WS closed', e); });
socket.addEventListener('error', function(e) { console.log('WS error', e); });
```

# Docs
https://ktor.io/servers/features/websockets.html
https://github.com/lensesio/kafka-cheat-sheet
https://dev.to/de_maric/how-to-delete-records-from-a-kafka-topic-464g
https://ktor.io/samples/app/chat.html
https://ktor.io/quickstart/guides/chat.html
