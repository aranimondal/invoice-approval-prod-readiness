using System.Reflection;
using InvoiceApp.Domain;
using Xunit;

namespace InvoiceApp.Tests;

/// <summary>
/// Structural rules that the target design depends on. See docs/architecture/as-built.md.
///
/// Rule: InvoiceApp.Domain holds business rules only. It must not reference web, storage
/// or transport code (AGENTS.md: "business rules, no web or storage code"). The outbox
/// design in ADR-001 relies on this: the approval decision and the outbox row are produced
/// by the domain, and only the Api project knows about SQL, HTTP and PayEx.
///
/// Run: dotnet test backend/InvoiceApp.sln --filter "FullyQualifiedName~ArchitectureTests"
/// </summary>
public class ArchitectureTests
{
    private static readonly Assembly DomainAssembly = typeof(ApprovalService).Assembly;

    // Prefixes of assembly names the domain is never allowed to reference.
    private static readonly string[] ForbiddenAssemblyPrefixes =
    {
        "Microsoft.AspNetCore",
        "Microsoft.EntityFrameworkCore",
        "Microsoft.Data.SqlClient",
        "System.Data.SqlClient",
        "Dapper",
        "DbUp",
        "Azure.",
        "Microsoft.Azure.",
        "System.Net.Http",
        "InvoiceApp.Api",
    };

    [Fact]
    public void Domain_DoesNotReferenceWebStorageOrTransportAssemblies()
    {
        var referenced = DomainAssembly
            .GetReferencedAssemblies()
            .Select(reference => reference.Name ?? string.Empty)
            .ToList();

        var violations = referenced
            .Where(name => ForbiddenAssemblyPrefixes.Any(prefix =>
                name.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)))
            .ToList();

        Assert.True(
            violations.Count == 0,
            "InvoiceApp.Domain must stay free of web, storage and transport code, but it references: "
            + string.Join(", ", violations)
            + ". Put the dependency behind an interface in Domain and implement it in InvoiceApp.Api.");
    }

    [Fact]
    public void Domain_TypesAllLiveInTheDomainNamespace()
    {
        var strays = DomainAssembly
            .GetTypes()
            .Where(type => type.IsPublic)
            .Where(type => type.Namespace is null
                           || !type.Namespace.StartsWith("InvoiceApp.Domain", StringComparison.Ordinal))
            .Select(type => type.FullName)
            .ToList();

        Assert.True(
            strays.Count == 0,
            "Public types in InvoiceApp.Domain must be in the InvoiceApp.Domain namespace: "
            + string.Join(", ", strays));
    }
}
