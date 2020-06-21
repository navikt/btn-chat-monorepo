# BTN-CHAT

# Run locally
Start kafka and all apps by running: `docker-compose up`

Start them independently by specifying what service you want to start: `docker-compose up --build kafka fss`  
`--build` forces rebuilding the app images, and is necessary after changes to the code. But otherwise not needed.

After starting kafka one can visit `http://localhost:3030/` to ensure it has started correctly.
   
Apps can also be started by running `StartFss` and `StartSbs` from you IDE. 
In these cases you only need to start kafka via docker-compose

# Testing to websocket
```javascript
var socket = new WebSocket('ws://localhost:7075/btn-chat-fss/api/chat/chatId123');

// Connection opened
socket.addEventListener('open', function (event) {
    console.log('WS open', event)
});

// Listen for messages
socket.addEventListener('message', function (event) {
    console.log('WS message ', event.data);
});

socket.addEventListener('close', function(e) { console.log('WS closed', e); });
socket.addEventListener('error', function(e) { console.log('WS error', e); });
```

For SBS-app use: `var socket = new WebSocket('ws://localhost:7076/btn-chat-sbs/api/chat/chatId123');`

To send a message: `socket.send('Your message here')`

## Contact us
Questions related to this service may be asking by creating an issue, or sending an email to one of the following people:
-   Jan-Eirik B. Nævdal, jan.eirik.b.navdal@nav.no
-   Andreas Bergman, andreas.bergman@nav.no
-   Nicklas Utgaard, nicklas.utgaard@nav.no
-   Ankur Tade, ankur.tade@nav.no

### For NAV-employees
You can reach the team through Slack at #team-personoversikt
