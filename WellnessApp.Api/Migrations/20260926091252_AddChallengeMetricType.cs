using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace WellnessApp.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddChallengeMetricType : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "MetricType",
                table: "Challenges",
                type: "text",
                nullable: false,
                defaultValue: "");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "MetricType",
                table: "Challenges");
        }
    }
}
