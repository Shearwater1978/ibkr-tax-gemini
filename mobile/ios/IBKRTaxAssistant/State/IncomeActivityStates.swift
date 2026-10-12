import Foundation

/// Imported dividend and withholding-tax rows plus the NBP rates used for USD totals.
struct DividendData: Equatable {
    var events: [CashEvent]
    var rates: FxRates?
}

/// Dividends for every screen (Android `IncomeState`); nil until the first load finished.
@MainActor
final class IncomeState: ObservableObject {
    @Published private(set) var data: DividendData?

    private let load: () async -> DividendData

    init(load: @escaping () async -> DividendData) {
        self.load = load
    }

    convenience init(container: AppContainer) {
        self.init {
            await Task.detached {
                // Rates come from the main page's NBP refresh; fetch once only if nothing is cached yet.
                var rates = try? container.fx().rates()
                if rates == nil, let fx = try? container.fx() {
                    _ = try? await fx.refresh()
                    rates = try? fx.rates()
                }
                return DividendData(events: (try? container.dividendEvents().all()) ?? [], rates: rates)
            }.value
        }
    }

    func reload() {
        Task { data = await load() }
    }
}

/// The activity list: newest first, searched and filtered (mobile-operations spec).
struct ActivityView: Equatable {
    var query = ""
    var filters: Set<OperationFilter> = []
    var items: [OperationRecord] = []
    var endReached = false
    /// Nil until known; false shows the empty state.
    var hasAny: Bool?
}

/// Paged operations (Android `ActivityState`); search typing is debounced and stale pages are dropped.
@MainActor
final class ActivityState: ObservableObject {
    static let pageSize = 100

    @Published private(set) var view = ActivityView()

    private let page: (Set<OperationFilter>, String, Int, Int) async -> [OperationRecord]
    private let hasAny: () async -> Bool
    private let debounce: TimeInterval
    private var first: Task<Void, Never>?
    private var more: Task<Void, Never>?

    init(page: @escaping (Set<OperationFilter>, String, Int, Int) async -> [OperationRecord], hasAny: @escaping () async -> Bool, debounce: TimeInterval = 0.25) {
        self.page = page
        self.hasAny = hasAny
        self.debounce = debounce
    }

    convenience init(container: AppContainer) {
        self.init(
            page: { filters, query, limit, offset in
                await Task.detached { (try? container.operations().page(filters: filters, tickerQuery: query, limit: limit, offset: offset)) ?? [] }.value
            },
            hasAny: { await Task.detached { (try? container.operations().hasAny()) ?? false }.value }
        )
    }

    /// Reloads the first page, e.g. when the screen opens or data changed.
    func reload() { restart(debounced: false, recheck: true) }

    func setQuery(_ query: String) {
        view.query = query
        restart(debounced: true)
    }

    func toggle(_ filter: OperationFilter) {
        if view.filters.contains(filter) { view.filters.remove(filter) } else { view.filters.insert(filter) }
        restart(debounced: false)
    }

    func clear() {
        view.query = ""
        view.filters = []
        restart(debounced: false)
    }

    /// Appends the next page; ignored while one loads, at the end, or before the first page.
    func loadMore() {
        let current = view
        if more != nil || current.endReached || current.items.isEmpty { return }
        more = Task {
            defer { more = nil }
            let next = await page(current.filters, current.query, Self.pageSize, current.items.count)
            // Drop the page if the search changed while it loaded.
            guard !Task.isCancelled, view.query == current.query, view.filters == current.filters, view.items.count == current.items.count else { return }
            view.items += next
            view.endReached = next.count < Self.pageSize
        }
    }

    /// `recheck` asks again whether any operation exists (data may have been imported or deleted).
    private func restart(debounced: Bool, recheck: Bool = false) {
        first?.cancel()
        more?.cancel()
        more = nil
        first = Task {
            if debounced { try? await Task.sleep(nanoseconds: UInt64(debounce * 1_000_000_000)) }
            if Task.isCancelled { return }
            let wanted = view
            let any: Bool
            if let known = wanted.hasAny, !recheck { any = known } else { any = await hasAny() }
            let items = await page(wanted.filters, wanted.query, Self.pageSize, 0)
            if Task.isCancelled { return }
            view.items = items
            view.endReached = items.count < Self.pageSize
            view.hasAny = any
        }
    }
}
