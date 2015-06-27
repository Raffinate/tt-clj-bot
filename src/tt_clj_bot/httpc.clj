(ns tt-clj-bot.httpc
  (:require [clj-http.client :as http-client]
            [clj-http.cookies :as http-cookies]
            [clj-http.conn-mgr :as http-cm]
            [clj-time.core]))

(declare update-account-id
         request
         request-with-query-params
         prepare-csrftoken
         make-session-p)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         TODO                       ;;
;; Understand which kind of exceptions can be thrown. ;;
;; Throw exceptions on error responses.               ;;
;; Or maybe leave such errors in response to leave    ;;
;; them for bot code.                                 ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;
;; THE TALE API ;;
;;;;;;;;;;;;;;;;;;

(defn make-session
  "Create default session. You may use login to create session that is
  already logged in."
  []
  (make-session-p))

(defn login
  "email - your email.
  password - your password.
  You can pass session as first argument to save data."
  ([email password] (login (make-session) email password))
  ([session email password]
   (update-account-id (request (prepare-csrftoken session)
                               :login
                               {:request-args {:form-params {:email email
                                                             :password password}}}))))

(defn logout
  "logout from session."
  [session]
  (request session :logout))

(defn game-info
  "Return almost all information about a character.
  Response body will be strored in :info."
  ([session]
   (request session :game-info))
  ([session account-id]
   (request-with-query-params session :game-info {:account account-id})))

(defn choose-quest
  "uid - unique decision identificator.
  You can find it in choice_alternatives.
  Response body will be stored in :choose-quest."
  [session decision-id]
  (request-with-query-params session :choose-quest {:option_uid decision-id}))

(defn get-card
  "Get new card.
  Response body will be stored in :get-card"
  [session]
  (request session :get-card))

(defn combine-cards
  "Combine cards together.
  card-ids - vector of card ids you want to combine.
  Response body will be stored in :combine-cards"
  [session & card-ids]
  (request-with-query-params session
                             :combine-cards {:cards
                                             (reduce str (interpose "," card-ids))}))

(defn raw-path-info
  "Requests raw path (http://the-tale.org/raw-path).
  This may be usefull to get monster description or
  processing status.
  Response might NOT be a json, So result may be not a MAP!
  Response body will be stored in :raw-path-info."
  [session path]
  (request session :raw-path-info {:method-path-args [path]
                                   :request-args {:as :auto}}))

(defn api-info
  "Return general info about API.
  Response body will be stored in :api-info."
  [session]
  (request session :api-info))

(defmulti use-ability
  "Uses ability. pass arguments of the ability in args.
  Abilities can be:
  :help :arena_pvp_1x1 :arena_pvp_1x1_leave_queue :drop_item
  :arena_pvp_1x1_accept (param: battle)
  :building_repair (param: building)
  Response body will be stored in :use-ability"
  (fn [session ability & args]
    (keyword ability)))

(defn help-character
  "Use energy to help your character.
  Response body will be stored in :use-ability."
  [session]
  (use-ability session :help))
  ;(request session :use {:method-path-args ["help"]}))

(defmethod use-ability :building_repair [session ability & args]
  (request session :use-ability {:method-path-args [(name ability)]
                                 :request-args {:query-params
                                                {:building (first args)}}}))

(defmethod use-ability :arena_pvp_1x1_accept [session ability & args]
  (request session :use-ability {:method-path-args [(name ability)]
                                 :request-args {:query-params
                                                {:battle (first args)}}}))

(defmethod use-ability :default [session ability & _ ]
  (request session :use-ability {:method-path-args [(name ability)]}))


(defn use-card
  "Use the card. Place map
  {:person idperson :place idpalce :building idbuilding}"
  [session card post-params]
  (request session :use-card {:request-args {:form-params post-params}}))

;;;;;;;;;;;;;
;; THE END ;;
;;;;;;;;;;;;;


(defn- interleave-between [a b]
  (cons (first a) (interleave b (rest a))))


(def ^:private +api-address+ "http://the-tale.org")

(def ^:private +api-settings+
  {:login {:method :post
           :path ["/accounts/auth/api/login"]
           :version "1.0"}
   :logout {:method :post
            :path ["/accounts/auth/api/logout"]
            :version "1.0"}
   :game-info {:method :get
          :path ["/game/api/info"]
          :version "1.3"}
   :use-ability {:method :post
                 :path ["/game/abilities/" "/api/use"]
                 :version "1.0"
                 }
   :choose-quest {:method :post
                  :path ["/game/quests/api/choose/"]
                  :version "1.0"}
   :get-card {:method :post
              :path ["/game/cards/api/get"]
              :version "1.0"}
   :combine-cards {:method :post
                   :path ["/game/cards/api/combine"]
                   :version "1.0"}
   :api-info {:method :get
              :path ["/api/info"]
              :version "1.0"}
   :raw-path-info {:method :get
                   :path ["" ""]
                   :version "1.0"}
   :use-card {:method :post
              :path ["/game/cards/api/use"]
              :version "1,0"}
   })

(defn- make-session-p []
  {:connection-manager (http-cm/make-reusable-conn-manager {:timeout 10
                                                            :insecure? false
                                                            :threads 1})
   :cookie-store (http-cookies/cookie-store)
   :app-id "tstbot"
   :app-version "0.1"
   :api-address +api-address+
   :api-settings +api-settings+
   :account-id nil ;account id (game account number)
   :response nil ;latest response
   :error nil ;error information
   :body (zipmap (keys +api-settings+) (repeat nil)) ;data got from response
   :session-created (clj-time.core/now)
   :update-time (clj-time.core/now)
   })

(defn- client-id [session]
  (str (:app-id session) "-" (:app-version session)))

(defn- make-api-path
  ([session api-method]
   (make-api-path session api-method nil))
  ([session api-method method-args]
   (reduce str (:api-address session)
           (interleave-between (-> session
                                   :api-settings
                                   api-method
                                   :path) method-args))))

(defn- make-account-query-params [session]
  (when (:account-id session)
    {:account (:account-id session)}))

(defn- make-query-params
  ([session api-method]
   (make-query-params session api-method nil))
  ([session api-method q-params]
   {:query-params (merge {:api_version (-> session
                                           :api-settings
                                           api-method
                                           :version)
                          :api_client (client-id session)}
                         (make-account-query-params session)
                         q-params)}))

(defn- get-csrftoken [session]
  (-> session
            :cookie-store
            http-cookies/get-cookies
            (get "csrftoken")
            :value))

(defn- make-headers
  ([session] (make-headers session nil))
  ([session headers]
   (let [csrftoken (get-csrftoken session)]
     (when csrftoken
       {:headers (merge {"X-CSRFToken" csrftoken}
                        headers)}))))


(defn- make-request-params [session api-method {:keys [request-args method-path-args]}]
  (merge {:method (-> session
                      :api-settings
                      api-method
                      :method)
          :url (make-api-path session api-method method-path-args)
          :as :json
          :connection-manager (:connection-manager session)
          :cookie-store (:cookie-store session)}
         (make-query-params session api-method (:query-params request-args))
         (make-headers session (:headers request-args))
         (dissoc request-args :query-params :headers)))

(defn- update-session [session api-method response]
  ;;TODO check for errors and throw exceptions
  (let [current-time (clj-time.core/now)]
  (merge session
         {:response response
          :body (merge (:body session) {api-method (-> response
                                                       :body
                                                       (assoc :update-time
                                                              current-time))})
          :update-time current-time})))

(defn- request
  "session - session from (make-session).
  api-method - one of +api-sessings+ keys.
  request-params - {:request-args {...}, :method-path-args [...]}
  request-args - map of additional http-client/request arguments.
  method-path-args - vector of strings that form a request path.
  return value - updated session."
  ([session api-method] (request session api-method {}))
  ([session api-method request-params]
   (update-session session api-method
                   (http-client/request (make-request-params session
                                                             api-method
                                                             request-params)))))

(defn request-with-query-params
  "Same as request but specify only query-params ('GET' params) map.
  This make meny usual requests shorter to program."
  [session api-method query-params]
  (request session api-method
           {:request-args {:query-params query-params}}))

(defn- prepare-csrftoken [session]
  (request session :api-info))

(defn- update-account-id [session]
  (assoc session :account-id (-> session
                                 :body
                                 :login
                                 :data
                                 :account_id)))

