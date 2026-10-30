# Customer phone app

The POS serves a phone app to customers on the store Wi-Fi. No app store install: customers open a link in their phone browser (and can "Add to Home Screen").

## What customers can do

- **Shop** - see every item's price, stock, and where it is in the store (aisle/shelf). No account needed.
- **Order** - sign up once (name, mobile number, e-mail, address, password), then order:
  - **Deliver to me** - to their address, with an optional map pin.
  - **I'm in the store** - order on their phone; staff bring the items to them (table, aisle, or counter pickup).
  - They pay on hand-over (cash, e-wallet or card).
- **Track** - live order status, and a Google Map with the store, their pin and the rider.
- **Stores** - all branches on a map, with distance, directions, call, and "order here".

## Running it

1. Start the POS as usual. It updates the database itself (adds `customers`, `stores`, `online_orders`, `online_order_items` and `products.location`) and starts the phone app on port **8080**.
2. Open **Orders** in the POS. The address phones should open is shown at the top (for example `http://192.168.1.6:8080/`). **Copy App Link** copies it.
3. The first time, Windows Firewall asks whether Java may accept connections: allow it on **Private networks**.

Change the port with the `mobile_port` setting (in the `settings` table) and restart the POS.

## Staff workflow (POS > Orders)

New orders beep and appear at the top. Select one, then use the main button:

| Delivery | In store |
| --- | --- |
| Accept order | Accept order |
| Send out for delivery | Mark ready - bring to customer |
| Delivered and paid | Handed over and paid |

The last step records a normal sale (it shows in History, prints like any receipt, and takes the stock). **Cancel order** works until then. Customers can cancel only while the order is still New.

### Rider tracking

For a delivery, **Copy rider link** and send it to the rider. On their phone they tap **Start sharing my location**, and the customer sees them move on the map. If the rider doesn't share GPS, the customer sees an *estimated* position along the route instead.

## Setup

- **Item locations**: Products screen > *Location in store*, for example `Aisle 3, Shelf B`.
- **Branches**: edit the `stores` table in phpMyAdmin (name, address, latitude, longitude, phone, hours). Three demo Iloilo City branches are added if the table is empty. Get coordinates by right-clicking a spot in Google Maps.
- **Google Maps key** (Settings > Customer phone app): create a key in Google Cloud Console with the **Maps JavaScript API** enabled. With a key, maps are interactive (tap to drop a delivery pin, live rider marker). Without one, the app shows simple Google Maps embeds.

## Phone GPS needs HTTPS

Browsers only share a phone's GPS location on `https://` pages (or `localhost`). On the plain `http://192.168...` address everything works except "Use my location", "Nearest to me" and rider GPS sharing. Customers can still tap the map to drop a pin, and riders show as an estimated position.

To get GPS, put the app behind HTTPS, for example with a tunnel such as `cloudflared tunnel --url http://localhost:8080`, and share the `https://` link it prints. That link also works outside the store Wi-Fi, so only use it while you need it.
