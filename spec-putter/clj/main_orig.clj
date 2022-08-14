(ns main-orig
  (:require
   [clojure.spec.alpha :as s]
   [reitit.coercion.spec :refer [coercion]]
   [reitit.ring.coercion :refer [coerce-exceptions-middleware
                                 coerce-request-middleware
                                 coerce-response-middleware]]
   [reitit.ring :as reitit-ring]
   [reitit.ring.middleware.dev :refer [print-request-diffs]]
   [org.httpkit.server :refer [run-server]]))

(s/def ::title string?)
(s/def ::description string?)
(s/def ::version int?)

(s/def ::foo
  (s/keys :req-un [::title ::description ::version]))

(s/def ::foo-update
  (s/keys :req-opt [::title ::description]))

(defonce foo (atom {:title "foo0"
                    :description "The initial description of foo"
                    :version 0}))

(defonce server (atom nil))

(defn get-foo [_]
  {:status 200
   :body @foo})

(defn put-foo [_]
  (swap! foo #(assoc % :version (-> % :version inc))))

(def routes
  ["/foo" {:get {:handler get-foo}
           :put {:handler put-foo}}])


(defn make-router []
  (reitit-ring/router
   routes
   {:reitit.middleware/transform print-request-diffs
    :data {:coercion coercion
           :middleware [coerce-exceptions-middleware
                        coerce-request-middleware
                        coerce-response-middleware]}}))

(defn make-app []
  (reitit-ring/ring-handler (make-router)))

(defn run []
  (swap! server (fn [old-server]
                  (when old-server
                    (old-server))
                  (run-server (make-app) {:port 8081}))))
