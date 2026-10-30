package com.pos.web;

import com.pos.db.CustomerDao;
import com.pos.db.DbErrors;
import com.pos.db.OnlineOrderDao;
import com.pos.db.OrderMessageDao;
import com.pos.db.ProductDao;
import com.pos.db.ProductImageDao;
import com.pos.db.SettingsDao;
import com.pos.db.StoreDao;
import com.pos.model.Customer;
import com.pos.model.OnlineOrder;
import com.pos.model.OnlineOrderItem;
import com.pos.model.OrderMessage;
import com.pos.model.OrderStatus;
import com.pos.model.OrderType;
import com.pos.model.PaymentMethod;
import com.pos.model.Product;
import com.pos.model.Store;
import com.pos.service.CustomerService;
import com.pos.service.OnlineOrderService;
import com.pos.service.ValidationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Serves the customer phone app (src/main/resources/mobile) and its JSON API on the store network,
 * from the same database the POS uses. Phones open http://&lt;this PC's IP&gt;:&lt;port&gt;/.
 */
public final class MobileServer {
    private static final Logger LOG = Logger.getLogger(MobileServer.class.getName());
    public static final int DEFAULT_PORT = 8080;
    private static final int MAX_BODY = 64 * 1024;
    private static final int MAX_FAILURES = 10;
    private static final long FAILURE_WINDOW_MS = 15 * 60 * 1000L;
    private static final Pattern ORDER_PATH = Pattern.compile("/api/orders/(\\d+)(/cancel)?");
    private static final Pattern MESSAGES_PATH = Pattern.compile("/api/orders/(\\d+)/messages");
    private static final Pattern PRODUCT_IMAGE_PATH = Pattern.compile("/api/products/(\\d+)/image");
    private static final Pattern RIDER_PATH = Pattern.compile("/api/rider/([A-Z0-9]{8})(/location)?");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
        "html", "text/html; charset=utf-8",
        "js", "text/javascript; charset=utf-8",
        "css", "text/css; charset=utf-8",
        "json", "application/json; charset=utf-8",
        "svg", "image/svg+xml",
        "png", "image/png",
        "webmanifest", "application/manifest+json"
    );
    private static HttpServer server;
    private static int port;
    private final ProductDao productDao = new ProductDao();
    private final StoreDao storeDao = new StoreDao();
    private final CustomerDao customerDao = new CustomerDao();
    private final OnlineOrderDao orderDao = new OnlineOrderDao();
    private final SettingsDao settingsDao = new SettingsDao();
    private final OrderMessageDao messageDao = new OrderMessageDao();
    private final ProductImageDao imageDao = new ProductImageDao();
    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    private MobileServer() {
    }

    public static synchronized void start(int listenPort) throws IOException {
        if (server == null) {
            AtomicInteger counter = new AtomicInteger();
            ExecutorService executor = Executors.newFixedThreadPool(8, runnable -> {
                Thread thread = new Thread(runnable, "pos-mobile-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
            HttpServer created = HttpServer.create(new InetSocketAddress(listenPort), 0);
            MobileServer handler = new MobileServer();
            created.createContext("/", handler::handle);
            created.setExecutor(executor);
            created.start();
            server = created;
            port = listenPort;
            LOG.info("Phone app is served on port " + listenPort + " at " + addresses());
        }
    }

    public static synchronized boolean isRunning() {
        return server != null;
    }

    public static synchronized int port() {
        return port;
    }

    /** Addresses phones on the same Wi-Fi can open, for example http://192.168.1.20:8080/. */
    public static List<String> addresses() {
        List<String> urls = new ArrayList<>();
        // VirtualBox, VMware, Hyper-V and WSL adapters only reach this PC, so phones can't use them; list them last.
        List<String> hostOnly = new ArrayList<>();

        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (nic.isUp() && !nic.isLoopback() && !nic.isVirtual()) {
                    String name = (nic.getDisplayName() + " " + nic.getName()).toLowerCase();
                    boolean virtualAdapter = name.matches(".*(virtualbox|vmware|hyper-v|vethernet|wsl|docker|host-only|tap-|tunnel).*");
                    for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                        if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                            (virtualAdapter ? hostOnly : urls).add("http://" + address.getHostAddress() + ":" + port + "/");
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Could not list network addresses", (Throwable)e);
        }

        urls.addAll(hostOnly);

        if (urls.isEmpty()) {
            urls.add("http://localhost:" + port + "/");
        }

        return urls;
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                this.api(ex, path);
            } else {
                this.staticFile(ex, path);
            }
        } catch (ApiError e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", e.getMessage());
            body.put("field", e.field);
            send(ex, e.status, body);
        } catch (ValidationException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", e.getMessage());
            body.put("field", e.getField());
            send(ex, 400, body);
        } catch (Exception | LinkageError e) {
            LOG.log(Level.WARNING, "Phone app request failed: " + ex.getRequestMethod() + " " + ex.getRequestURI(), (Throwable)e);
            send(ex, 500, Map.of("error", "Something went wrong on the store server. Please try again."));
        } finally {
            ex.close();
        }
    }

    private void api(HttpExchange ex, String path) throws Exception {
        String method = ex.getRequestMethod();
        Matcher order = ORDER_PATH.matcher(path);
        Matcher rider = RIDER_PATH.matcher(path);
        Matcher messages = MESSAGES_PATH.matcher(path);
        Matcher image = PRODUCT_IMAGE_PATH.matcher(path);
        if (path.equals("/api/config") && method.equals("GET")) {
            send(ex, 200, this.config());
        } else if (path.equals("/api/products") && method.equals("GET")) {
            send(ex, 200, this.productDao.findAll(true).stream().map(MobileServer::productJson).toList());
        } else if (image.matches() && method.equals("GET")) {
            ProductImageDao.StoredImage stored = this.imageDao.find(Integer.parseInt(image.group(1)))
                .orElseThrow(() -> new ApiError(404, "No photo", null));
            // The link carries the photo's version (?v=...), so phones can keep it until it changes.
            ex.getResponseHeaders().set("Content-Type", stored.contentType());
            ex.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
            ex.sendResponseHeaders(200, (long)stored.data().length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(stored.data());
            }
        } else if (path.equals("/api/stores") && method.equals("GET")) {
            send(ex, 200, this.storeDao.findActive().stream().map(MobileServer::storeJson).toList());
        } else if (path.equals("/api/register") && method.equals("POST")) {
            send(ex, 201, this.register(body(ex)));
        } else if (path.equals("/api/login") && method.equals("POST")) {
            String key = "login:" + clientAddress(ex);
            this.checkAttempts(key);
            try {
                send(ex, 200, this.login(body(ex)));
            } catch (ApiError e) {
                if (e.status == 401) {
                    this.recordFailure(key);
                }
                throw e;
            }
        } else if (path.equals("/api/logout") && method.equals("POST")) {
            this.customerDao.setToken(this.customer(ex).id(), null);
            send(ex, 200, Map.of("ok", true));
        } else if (path.equals("/api/me") && method.equals("GET")) {
            send(ex, 200, customerJson(this.customer(ex)));
        } else if (path.equals("/api/me") && method.equals("PUT")) {
            send(ex, 200, this.updateProfile(this.customer(ex), body(ex)));
        } else if (path.equals("/api/orders") && method.equals("GET")) {
            Customer customer = this.customer(ex);
            send(ex, 200, this.orderDao.findForCustomer(customer.id()).stream().map(o -> orderJson(o, null)).toList());
        } else if (path.equals("/api/orders") && method.equals("POST")) {
            send(ex, 201, this.placeOrder(this.customer(ex), body(ex)));
        } else if (order.matches() && order.group(2) == null && method.equals("GET")) {
            send(ex, 200, this.customerOrder(ex, Integer.parseInt(order.group(1))));
        } else if (order.matches() && order.group(2) != null && method.equals("POST")) {
            send(ex, 200, this.cancel(ex, Integer.parseInt(order.group(1))));
        } else if (messages.matches() && method.equals("GET")) {
            send(ex, 200, this.messages(ex, Integer.parseInt(messages.group(1))));
        } else if (messages.matches() && method.equals("POST")) {
            send(ex, 201, this.sendMessage(ex, Integer.parseInt(messages.group(1))));
        } else if (rider.matches() && rider.group(2) == null && method.equals("GET")) {
            String key = "rider:" + clientAddress(ex);
            this.checkAttempts(key);
            try {
                send(ex, 200, this.riderOrder(rider.group(1)));
            } catch (ApiError e) {
                if (e.status == 404) {
                    this.recordFailure(key);
                }
                throw e;
            }
        } else if (rider.matches() && rider.group(2) != null && method.equals("POST")) {
            Map<String, Object> body = body(ex);
            BigDecimal lat = coordinate(body.get("lat"), 90);
            BigDecimal lng = coordinate(body.get("lng"), 180);
            if (lat == null || lng == null) {
                throw new ApiError(400, "Location is missing", null);
            }

            boolean saved = this.orderDao.updateRiderLocation(rider.group(1), lat, lng);
            send(ex, 200, Map.of("tracking", saved));
        } else {
            throw new ApiError(404, "Not found", null);
        }
    }

    private Map<String, Object> config() throws SQLException {
        Map<String, String> settings = this.settingsDao.getAll();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("storeName", settings.getOrDefault("store_name", "My Store"));
        config.put("mapsKey", settings.getOrDefault("google_maps_api_key", ""));
        config.put("currency", NumberFormat.getCurrencyInstance().getCurrency().getCurrencyCode());
        config.put("taxRate", settings.getOrDefault("tax_rate", "0"));
        config.put("pricesIncludeTax", settings.getOrDefault("prices_include_tax", "false"));        return config;
    }

    private Map<String, Object> register(Map<String, Object> body) throws Exception {
        Customer profile = CustomerService.validateProfile(text(body, "fullName"), text(body, "phone"), text(body, "email"), text(body, "address"));
        String password = text(body, "password");
        CustomerService.validatePassword(password);
        String salt = CustomerService.newSalt();
        String token = CustomerService.newToken();

        int id;
        try {
            id = this.customerDao.insert(
                profile.fullName(),
                profile.phone(),
                profile.email(),
                profile.address(),
                CustomerService.hashPassword(password, salt),
                salt,
                CustomerService.hashToken(token)
            );
        } catch (SQLException e) {
            if (DbErrors.isDuplicateKey(e)) {
                throw new ApiError(409, "This mobile number or e-mail already has an account. Please sign in.", "phone");
            }

            throw e;
        }

        return session(token, new Customer(id, profile.fullName(), profile.phone(), profile.email(), profile.address()));
    }

    private Map<String, Object> login(Map<String, Object> body) throws Exception {
        String login = text(body, "login").toLowerCase();
        if (!login.contains("@")) {
            login = login.replaceAll("[\\s()-]", "");
        }

        Optional<CustomerDao.Credentials> found = login.isEmpty() ? Optional.empty() : this.customerDao.findCredentials(login);
        if (found.isEmpty() || !CustomerService.passwordMatches(text(body, "password"), found.get().salt(), found.get().passwordHash())) {
            throw new ApiError(401, "Wrong mobile number, e-mail or password", "login");
        } else {
            String token = CustomerService.newToken();
            this.customerDao.setToken(found.get().customer().id(), CustomerService.hashToken(token));
            return session(token, found.get().customer());
        }
    }

    private Map<String, Object> updateProfile(Customer customer, Map<String, Object> body) throws Exception {
        Customer profile = CustomerService.validateProfile(text(body, "fullName"), text(body, "phone"), text(body, "email"), text(body, "address"));

        try {
            this.customerDao.updateProfile(customer.id(), profile.fullName(), profile.phone(), profile.email(), profile.address());
        } catch (SQLException e) {
            if (DbErrors.isDuplicateKey(e)) {
                throw new ApiError(409, "Another account already uses this mobile number or e-mail", "phone");
            }

            throw e;
        }

        return customerJson(new Customer(customer.id(), profile.fullName(), profile.phone(), profile.email(), profile.address()));
    }

    private Map<String, Object> placeOrder(Customer customer, Map<String, Object> body) throws Exception {
        OrderType type = enumValue(OrderType.class, body.get("type"), "type", "Choose delivery or in-store");
        PaymentMethod payment = enumValue(PaymentMethod.class, body.getOrDefault("payment", "CASH"), "payment", "Choose a payment method");
        int storeId = wholeNumber(body.get("storeId"), "storeId", "Choose a store");
        Store store = this.storeDao.findActive(storeId).orElseThrow(() -> new ApiError(400, "That store is not available", "storeId"));
        if (!(body.get("items") instanceof List<?> rawItems) || rawItems.isEmpty()) {
            throw new ApiError(400, "Your cart is empty", "items");
        } else if (rawItems.size() > OnlineOrderService.MAX_LINES) {
            throw new ApiError(400, "Too many different items in one order", "items");
        }

        Map<Integer, Product> products = this.productDao.findAll(true).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Integer, Integer> quantities = new LinkedHashMap<>();

        for (Object raw : rawItems) {
            if (!(raw instanceof Map<?, ?> item)) {
                throw new ApiError(400, "Cart item is not valid", "items");
            }

            int productId = wholeNumber(item.get("productId"), "items", "Cart item is not valid");
            int quantity = wholeNumber(item.get("quantity"), "items", "Quantity must be a whole number");
            quantities.merge(productId, quantity, Integer::sum);
        }

        List<OnlineOrderItem> lines = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            Product product = products.get(entry.getKey());
            int quantity = entry.getValue();
            if (product == null) {
                throw new ApiError(409, "An item in your cart is no longer sold. Please refresh the product list.", "items");
            } else if (quantity < 1 || quantity > OnlineOrderService.MAX_QUANTITY) {
                throw new ApiError(400, "Quantity of " + product.getName() + " must be 1 to " + OnlineOrderService.MAX_QUANTITY, "items");
            } else if (quantity > product.getStock()) {
                String left = product.getStock() == 0 ? "is out of stock" : "has only " + product.getStock() + " left";
                throw new ApiError(409, product.getName() + " " + left, "items");
            }

            lines.add(OnlineOrderService.line(product, quantity));
        }

        Map<String, String> settings = this.settingsDao.getAll();
        OnlineOrderService.Totals totals = OnlineOrderService.price(lines, settings.get("tax_rate"), settings.get("prices_include_tax"));
        OnlineOrder order = new OnlineOrder();
        order.setCustomerId(customer.id());
        order.setStoreId(store.id());
        order.setType(type);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod(payment);
        order.setSubtotal(totals.subtotal());
        order.setTax(totals.tax());
        order.setTotal(totals.total());
        String note = limit(text(body, "note"), 255, "note", "Note");
        order.setNote(note.isEmpty() ? null : note);
        order.setRiderCode(OnlineOrderService.newRiderCode());
        order.setCreatedAt(LocalDateTime.now());
        order.setItems(lines);
        if (type == OrderType.DELIVERY) {
            String address = limit(text(body, "address"), 255, "address", "Delivery address");
            if (address.isEmpty()) {
                throw new ApiError(400, "Enter the delivery address", "address");
            }

            order.setDeliveryAddress(address);
            order.setDeliveryLat(coordinate(body.get("lat"), 90));
            order.setDeliveryLng(coordinate(body.get("lng"), 180));
        } else {
            String spot = limit(text(body, "spot"), 100, "spot", "Where you are");
            order.setInStoreSpot(spot.isEmpty() ? "Pick up at the counter" : spot);
        }

        this.orderDao.create(order);
        order.setCustomerName(customer.fullName());
        order.setCustomerPhone(customer.phone());
        order.setStoreName(store.name());
        return orderJson(order, store);
    }

    private Map<String, Object> customerOrder(HttpExchange ex, int orderId) throws Exception {
        Customer customer = this.customer(ex);
        OnlineOrder order = this.orderDao.findById(orderId).filter(o -> o.getCustomerId() == customer.id())
            .orElseThrow(() -> new ApiError(404, "Order not found", null));
        return orderJson(order, this.storeDao.findActive(order.getStoreId()).orElse(null));
    }

    private Map<String, Object> cancel(HttpExchange ex, int orderId) throws Exception {
        Customer customer = this.customer(ex);
        OnlineOrder order = this.orderDao.findById(orderId).filter(o -> o.getCustomerId() == customer.id())
            .orElseThrow(() -> new ApiError(404, "Order not found", null));
        if (!this.orderDao.changeStatus(orderId, OrderStatus.PENDING, OrderStatus.CANCELLED, null)) {
            throw new ApiError(409, "The store has already started on this order. Please call the store to cancel it.", null);
        } else {
            order.setStatus(OrderStatus.CANCELLED);
            return orderJson(order, this.storeDao.findActive(order.getStoreId()).orElse(null));
        }
    }

    /** The chat of one of the signed-in customer's orders; opening it marks the store's replies as read. */
    private List<Map<String, Object>> messages(HttpExchange ex, int orderId) throws Exception {
        this.ownOrder(this.customer(ex), orderId);
        this.messageDao.markRead(orderId, false);
        return this.messageDao.findForOrder(orderId).stream().map(MobileServer::messageJson).toList();
    }

    private Map<String, Object> sendMessage(HttpExchange ex, int orderId) throws Exception {
        Customer customer = this.customer(ex);
        this.ownOrder(customer, orderId);
        String text = limit(text(body(ex), "text"), OrderMessage.MAX_LENGTH, "text", "Message");
        if (text.isEmpty()) {
            throw new ApiError(400, "Type a message first", "text");
        }

        return messageJson(this.messageDao.add(orderId, false, null, text));
    }

    private OnlineOrder ownOrder(Customer customer, int orderId) throws Exception {
        return this.orderDao.findById(orderId).filter(o -> o.getCustomerId() == customer.id())
            .orElseThrow(() -> new ApiError(404, "Order not found", null));
    }

    private Map<String, Object> riderOrder(String riderCode) throws Exception {
        OnlineOrder order = this.orderDao.findByRiderCode(riderCode).filter(o -> o.getType() == OrderType.DELIVERY)
            .orElseThrow(() -> new ApiError(404, "No delivery has this rider code", null));
        Map<String, Object> json = orderJson(order, this.storeDao.findActive(order.getStoreId()).orElse(null));
        json.put("customerName", order.getCustomerName());
        json.put("customerPhone", order.getCustomerPhone());
        return json;
    }

    /** The phone's address. Behind the secure link every request arrives from this PC, and Cloudflare passes the phone's address in a header. */
    private static String clientAddress(HttpExchange ex) {
        InetAddress remote = ex.getRemoteAddress().getAddress();
        String forwarded = ex.getRequestHeaders().getFirst("CF-Connecting-IP");
        return remote.isLoopbackAddress() && forwarded != null && !forwarded.isBlank() ? forwarded.trim() : remote.getHostAddress();
    }

    /** Stops password and rider-code guessing, which matters once the app is reachable from the internet. */
    private void checkAttempts(String key) {
        Deque<Long> times = this.failures.get(key);
        if (times != null) {
            synchronized (times) {
                long cutoff = System.currentTimeMillis() - FAILURE_WINDOW_MS;
                while (!times.isEmpty() && times.peekFirst() < cutoff) {
                    times.pollFirst();
                }

                if (times.size() >= MAX_FAILURES) {
                    throw new ApiError(429, "Too many wrong tries. Please wait 15 minutes and try again.", null);
                }
            }
        }
    }

    private void recordFailure(String key) {
        Deque<Long> times = this.failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(System.currentTimeMillis());
        }
    }

    /** The signed-in customer, from the "Authorization: Bearer &lt;token&gt;" header. */
    private Customer customer(HttpExchange ex) throws Exception {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ") && header.length() > 7) {
            return this.customerDao.findByToken(CustomerService.hashToken(header.substring(7).trim()))
                .orElseThrow(() -> new ApiError(401, "Please sign in again", null));
        } else {
            throw new ApiError(401, "Please sign in first", null);
        }
    }

    private static Map<String, Object> session(String token, Customer customer) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("token", token);
        json.put("customer", customerJson(customer));
        return json;
    }

    private static Map<String, Object> customerJson(Customer customer) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", customer.id());
        json.put("fullName", customer.fullName());
        json.put("phone", customer.phone());
        json.put("email", customer.email());
        json.put("address", customer.address());
        return json;
    }

    private static Map<String, Object> productJson(Product product) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", product.getId());
        json.put("code", product.getCode());
        json.put("name", product.getName());
        json.put("category", product.getCategoryName());
        json.put("price", product.getPrice());
        json.put("stock", product.getStock());
        json.put("lowStock", product.isLowStock());
        json.put("location", product.getLocation());
        json.put("image", product.hasImage() ? "api/products/" + product.getId() + "/image?v=" + product.getImageVersion() : null);
        return json;
    }

    private static Map<String, Object> storeJson(Store store) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", store.id());
        json.put("name", store.name());
        json.put("address", store.address());
        json.put("lat", store.latitude());
        json.put("lng", store.longitude());
        json.put("phone", store.phone());
        json.put("hours", store.hours());
        return json;
    }

    private static Map<String, Object> orderJson(OnlineOrder order, Store store) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", order.getId());
        json.put("orderNo", order.getOrderNo());
        json.put("type", order.getType());
        json.put("status", order.getStatus());
        json.put("statusLabel", order.getStatus().label());
        json.put("payment", order.getPaymentMethod());
        json.put("subtotal", order.getSubtotal());
        json.put("tax", order.getTax());
        json.put("total", order.getTotal());
        json.put("itemCount", order.itemCount());
        json.put("storeId", order.getStoreId());
        json.put("storeName", order.getStoreName());
        json.put("address", order.getDeliveryAddress());
        json.put("lat", order.getDeliveryLat());
        json.put("lng", order.getDeliveryLng());
        json.put("spot", order.getInStoreSpot());
        json.put("note", order.getNote());
        json.put("createdAt", order.getCreatedAt());
        json.put("updatedAt", order.getUpdatedAt());
        json.put("dispatchedAt", order.getDispatchedAt());
        json.put("unreadMessages", order.getUnreadByCustomer());
        if (order.getRiderLat() != null) {
            json.put("rider", Map.of("lat", order.getRiderLat(), "lng", order.getRiderLng(), "updatedAt", order.getRiderUpdatedAt()));
        }

        if (store != null) {
            json.put("store", storeJson(store));
        }

        if (!order.getItems().isEmpty()) {
            List<Map<String, Object>> items = new ArrayList<>();

            for (OnlineOrderItem item : order.getItems()) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("productId", item.productId());
                line.put("name", item.productName());
                line.put("quantity", item.quantity());
                line.put("unitPrice", item.unitPrice());
                line.put("lineTotal", item.lineTotal());
                items.add(line);
            }

            json.put("items", items);
        }

        return json;
    }

    private static Map<String, Object> messageJson(OrderMessage message) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", message.id());
        json.put("fromStore", message.fromStaff());
        json.put("staffName", message.staffName());
        json.put("text", message.body());
        json.put("createdAt", message.createdAt());
        return json;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(HttpExchange ex) throws IOException {
        byte[] bytes;
        try (InputStream in = ex.getRequestBody()) {
            bytes = in.readNBytes(MAX_BODY + 1);
        }

        if (bytes.length > MAX_BODY) {
            throw new ApiError(413, "Request is too large", null);
        }

        try {
            Object parsed = Json.parse(new String(bytes, StandardCharsets.UTF_8));
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>)map;
            }
        } catch (IllegalArgumentException e) {
            LOG.log(Level.FINE, "Bad JSON from the phone app", (Throwable)e);
        }

        throw new ApiError(400, "Request is not valid JSON", null);
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private static String limit(String value, int max, String field, String label) {
        if (value.length() > max) {
            throw new ApiError(400, label + " can be at most " + max + " characters", field);
        } else {
            return value;
        }
    }

    private static int wholeNumber(Object value, String field, String message) {
        try {
            if (value instanceof BigDecimal d) {
                return d.intValueExact();
            } else if (value instanceof String s) {
                return Integer.parseInt(s.trim());
            }
        } catch (ArithmeticException | NumberFormatException e) {
            // fall through to the error below
        }

        throw new ApiError(400, message, field);
    }

    private static BigDecimal coordinate(Object value, int max) {
        if (value instanceof BigDecimal d && d.abs().compareTo(BigDecimal.valueOf((long)max)) <= 0) {
            return d.setScale(7, RoundingMode.HALF_UP);
        } else {
            return null;
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Object value, String field, String message) {
        try {
            return Enum.valueOf(type, String.valueOf(value));
        } catch (IllegalArgumentException e) {
            throw new ApiError(400, message, field);
        }
    }

    private void staticFile(HttpExchange ex, String path) throws IOException {
        if (!ex.getRequestMethod().equals("GET") && !ex.getRequestMethod().equals("HEAD")) {
            send(ex, 405, Map.of("error", "Method not allowed"));
        } else {
            String file = path.equals("/") ? "index.html" : path.substring(1);
            if (!file.matches("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*\\.[a-z]+")) {
                sendText(ex, 404, "Not found");
            } else {
                try (InputStream in = MobileServer.class.getResourceAsStream("/mobile/" + file)) {
                    if (in == null) {
                        sendText(ex, 404, "Not found");
                    } else {
                        byte[] bytes = in.readAllBytes();
                        String extension = file.substring(file.lastIndexOf('.') + 1);
                        ex.getResponseHeaders().set("Content-Type", CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"));
                        ex.getResponseHeaders().set("Cache-Control", "no-cache");
                        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                        ex.sendResponseHeaders(200, ex.getRequestMethod().equals("HEAD") ? -1L : (long)bytes.length);
                        if (!ex.getRequestMethod().equals("HEAD")) {
                            try (OutputStream out = ex.getResponseBody()) {
                                out.write(bytes);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void send(HttpExchange ex, int status, Object json) throws IOException {
        byte[] bytes = Json.write(json).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, (long)bytes.length);

        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendText(HttpExchange ex, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, (long)bytes.length);

        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static final class ApiError extends RuntimeException {
        private final int status;
        private final String field;

        ApiError(int status, String message, String field) {
            super(message);
            this.status = status;
            this.field = field;
        }
    }
}
