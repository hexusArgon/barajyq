import UIKit

/// External links: 2GIS route (as on Android) and source pages.
enum Links {
    /// Path-segment encoding (keeps 2GIS path separators out of the value).
    private static func enc(_ s: String) -> String {
        s.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "/?&=#,;|"))) ?? s
    }

    /// 2GIS links for the "Маршрут" button: (app deep link, web fallback). Same as Android `twoGisUrls`.
    /// With coordinates: official `dgis://2gis.ru/routeSearch/rsType/car/to/{lon},{lat}╎{name}` —
    /// start is the current location, "Куда поедем" is filled with the place.
    /// Without coordinates: 2GIS search by "place, address, city".
    static func twoGisUrls(_ e: EventCard, cityId: String?, cityName: String?) -> (app: String, web: String) {
        let slug = (cityId?.isEmpty == false ? cityId! : "astana")
        if let lat = e.lat, let lon = e.lon {
            let point = "\(lon),\(lat)"
            let name = enc(e.placeRu.isEmpty ? e.address : e.placeRu)
            return ("dgis://2gis.ru/routeSearch/rsType/car/to/\(point)%E2%95%8E\(name)",
                    "https://2gis.kz/\(slug)/directions/points/%7C\(lon)%2C\(lat)")
        }
        let q = enc(e.routeQuery(cityName: cityName))
        return ("dgis://2gis.ru/search/\(q)", "https://2gis.kz/\(slug)/search/\(q)")
    }

    /// Tries the 2GIS app (`dgis://`), falls back to the 2gis.kz web version.
    @MainActor
    static func open2Gis(_ e: EventCard, cityId: String?, cityName: String?) {
        let u = twoGisUrls(e, cityId: cityId, cityName: cityName)
        let web = URL(string: u.web)
        guard let app = URL(string: u.app) else {
            if let web { UIApplication.shared.open(web) }
            return
        }
        UIApplication.shared.open(app, options: [:]) { ok in
            if !ok, let web { UIApplication.shared.open(web) }
        }
    }

    @MainActor
    static func open(_ s: String) {
        if let url = URL(string: s) { UIApplication.shared.open(url) }
    }
}
